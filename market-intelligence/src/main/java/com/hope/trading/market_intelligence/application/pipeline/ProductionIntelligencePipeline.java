package com.hope.trading.market_intelligence.application.pipeline;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.persistence.*;
import com.hope.trading.market_intelligence.application.observation.ObservationBuilder;
import com.hope.trading.market_intelligence.application.opportunity.*;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import com.hope.trading.market_intelligence.strategy.application.BuiltinStrategies;
import com.hope.trading.market_intelligence.strategy.application.LiveStrategyEvaluationRunner;
import com.hope.trading.market_intelligence.strategy.application.ShadowStrategyParityMonitor;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchPersister;
import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Production intelligence pipeline (Story 0013 generalized model).
 *
 * <p>One atomic transaction T1: Observation evidence, StrategyEvaluation
 * (one per applicable strategy), StrategyMatch persistence for each MATCH,
 * TradingOpportunity creation for each match, and PipelineRun finalization
 * all commit together or roll back together.</p>
 *
 * <p>The pipeline evaluates ALL applicable strategies and preserves each
 * truthful MATCH independently. No ranking or selection policy is introduced.
 * PipelineRun stores opportunityId only when exactly one opportunity is created;
 * when zero or multiple opportunities exist, opportunityId is null. All matches
 * and opportunities are persisted independently.</p>
 *
 * <p>The pipeline is strategy-agnostic: it does not inspect concrete Strategy
 * IDs. Strategy-specific behavior lives in StrategyEvaluator implementations
 * and declarative StrategyDefinition metadata.</p>
 */
@Service
public class ProductionIntelligencePipeline {
    private static final Logger log = LoggerFactory.getLogger(ProductionIntelligencePipeline.class);
    public static final String VERSION = "production-intelligence/v1";
    private final ObservationBuilder observations;
    private final OpportunityEngine opportunities;
    private final MarketDataClient marketData;
    private final JpaIntelligencePipelineRunRepository runs;
    private final Clock clock;
    private final LiveStrategyEvaluationRunner strategyEvaluation;
    private final ShadowStrategyParityMonitor parity;
    private final StrategyMatchPersister matches;
    private final StrategyMatchOpportunityFactory matchOpportunities;
    private final BuiltinStrategies builtins;

    public ProductionIntelligencePipeline(
            ObservationBuilder observations, OpportunityEngine opportunities,
            MarketDataClient marketData, JpaIntelligencePipelineRunRepository runs,
            Clock clock, LiveStrategyEvaluationRunner strategyEvaluation,
            ShadowStrategyParityMonitor parity, StrategyMatchPersister matches,
            StrategyMatchOpportunityFactory matchOpportunities,
            BuiltinStrategies builtins) {
        this.observations = observations;
        this.opportunities = opportunities;
        this.marketData = marketData;
        this.runs = runs;
        this.clock = clock;
        this.strategyEvaluation = strategyEvaluation;
        this.parity = parity;
        this.matches = matches;
        this.matchOpportunities = matchOpportunities;
        this.builtins = builtins;
    }

    @Transactional
    public synchronized JpaIntelligencePipelineRunEntity process(
            UUID analysisExecutionId, UUID marketId, AnalysisExecutionMode mode) {
        return runs.findByAnalysisExecutionIdAndPipelineVersion(
                analysisExecutionId, VERSION).orElseGet(() -> execute(
                        analysisExecutionId, marketId, mode));
    }

    private JpaIntelligencePipelineRunEntity execute(
            UUID analysisExecutionId, UUID marketId, AnalysisExecutionMode mode) {
        JpaIntelligencePipelineRunEntity run = runs.save(
                JpaIntelligencePipelineRunEntity.running(
                        analysisExecutionId, VERSION, clock.instant()));
        String instrument;
        try {
            instrument = marketData.findMarket(marketId).symbol();
            if (instrument == null || instrument.isBlank()) {
                throw new IllegalStateException("Market instrument is unavailable");
            }
        } catch (RuntimeException exception) {
            run.fail("OBSERVATION", controlledMessage(exception), clock.instant());
            return runs.save(run);
        }
        Observation observation;
        try {
            observation = observations.build(
                    analysisExecutionId, instrument, new OhlcTrendObservationRule());
        } catch (java.util.NoSuchElementException exception) {
            run.noSignal(exception.getMessage(), clock.instant());
            return runs.save(run);
        } catch (RuntimeException exception) {
            run.fail("OBSERVATION", controlledMessage(exception), clock.instant());
            return runs.save(run);
        }

        List<StrategyDefinition> allStrategies = builtins.all();
        // Governance gate first (ADR-036): only domain-eligible strategies
        // participate in live evaluation. An ineligible strategy is simply not
        // selected — it never reaches an evaluator and never produces
        // NO_MATCH or NOT_EVALUABLE.
        List<StrategyDefinition> governedStrategies = allStrategies.stream()
                .filter(StrategyDefinition::isEligibleForLiveEvaluation)
                .toList();
        String provider = marketData.findMarket(marketId).provider();
        String timeframe = observation.horizon();
        // Market applicability second (ADR-035 I-8): timeframe/provider fit.
        // Governance and applicability are independent selection filters.
        List<StrategyDefinition> applicableStrategies = governedStrategies.stream()
                .filter(definition -> isApplicable(definition, timeframe, provider))
                .toList();
        Instant evaluatedAt = clock.instant();
        List<TradingOpportunity> createdOpportunities = new ArrayList<>();

        for (StrategyDefinition definition : applicableStrategies) {
            var evaluation = strategyEvaluation.evaluate(
                    definition, observation, marketId, evaluatedAt);

            // Shadow parity for the bootstrap legacy strategy during transition.
            if (BuiltinStrategies.LEGACY_OHLC_TREND_ID.equals(
                    definition.strategyId().value())) {
                parity.compareWithLegacyDecision(evaluation, observation, marketId);
            }

            if (evaluation.status() == StrategyEvaluationStatus.MATCH) {
                TradingOpportunity opportunity = handleMatch(
                        evaluation, definition, analysisExecutionId, observation,
                        instrument, mode);
                createdOpportunities.add(opportunity);
                log.info("Strategy {}v{} MATCH for market {}: created opportunity {}",
                        definition.name(), definition.version(), marketId,
                        opportunity.id());
            } else if (evaluation.status() == StrategyEvaluationStatus.NO_MATCH) {
                log.debug("Strategy {}v{} NO_MATCH for market {}",
                        definition.name(), definition.version(), marketId);
            } else {
                // NOT_EVALUABLE / FAILED: log and continue to next strategy.
                log.debug("Strategy {}v{} {} for market {}: {}",
                        definition.name(), definition.version(),
                        evaluation.status(), marketId, evaluation.explanation());
            }
        }

        if (createdOpportunities.isEmpty()) {
            run.noSignal("No strategy produced a matching setup", clock.instant());
        } else if (createdOpportunities.size() == 1) {
            TradingOpportunity only = createdOpportunities.getFirst();
            run.complete(observation.id(), observation.version(), only.id().value(),
                    only.version().value(), clock.instant());
        } else {
            run.complete(observation.id(), observation.version(), null, 0, clock.instant());
        }
        return runs.save(run);
    }

    private TradingOpportunity handleMatch(
            StrategyEvaluation evaluation,
            StrategyDefinition definition,
            UUID analysisExecutionId,
            Observation observation,
            String instrument,
            AnalysisExecutionMode mode
    ) {
        var persisted = matches.persist(
                evaluation, analysisExecutionId, observation.id()).orElseThrow();
        CreateOpportunityCommand command = matchOpportunities.command(
                persisted.match(), definition, instrument, originOf(mode),
                new ObservationReference(observation.id()),
                observation.validFrom(),
                observation.validFrom(),
                observation.validUntil().orElse(null));
        OpportunityCreationResult created = opportunities.create(command);
        TradingOpportunity analyzed = opportunities.transition(
                created.opportunity().id(), OpportunityStatus.ANALYZED);
        return opportunities.transition(analyzed.id(), OpportunityStatus.ACTIVE);
    }

    private static OpportunityOrigin originOf(AnalysisExecutionMode mode) {
        return mode == AnalysisExecutionMode.PASSIVE
                ? OpportunityOrigin.PASSIVE_SCAN : OpportunityOrigin.USER_REQUEST;
    }

    private static boolean isApplicable(StrategyDefinition definition, String timeframe, String provider) {
        StrategyApplicability applicability = definition.applicability();
        boolean timeframeApplicable = timeframe != null && !timeframe.isBlank()
                && applicability.timeframes().stream()
                    .anyMatch(t -> t == StrategyApplicability.Timeframe.parse(timeframe));
        boolean providerApplicable = applicability.providers().isEmpty()
                || (provider != null && !provider.isBlank()
                    && applicability.providers().stream()
                        .anyMatch(p -> p.equalsIgnoreCase(provider)));
        return timeframeApplicable && providerApplicable;
    }

    private String controlledMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }
}
