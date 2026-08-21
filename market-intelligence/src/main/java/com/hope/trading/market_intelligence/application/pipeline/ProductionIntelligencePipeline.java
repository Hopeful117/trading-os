package com.hope.trading.market_intelligence.application.pipeline;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.persistence.*;
import com.hope.trading.market_intelligence.application.observation.ObservationBuilder;
import com.hope.trading.market_intelligence.application.opportunity.*;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import com.hope.trading.market_intelligence.strategy.application.LiveStrategyEvaluationRunner;
import com.hope.trading.market_intelligence.strategy.application.ShadowStrategyParityMonitor;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchPersister;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/**
 * Production intelligence pipeline (Story 0012 authority model).
 *
 * <p>One atomic transaction T1: Observation evidence, StrategyEvaluation,
 * required StrategyMatch persistence/reuse, TradingOpportunity derivation from
 * the match and PipelineRun finalization all commit together or roll back
 * together. The legacy OHLC rule no longer decides whether an opportunity
 * exists; only a StrategyEvaluation MATCH does.</p>
 */
@Service
public class ProductionIntelligencePipeline {
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

    public ProductionIntelligencePipeline(
            ObservationBuilder observations, OpportunityEngine opportunities,
            MarketDataClient marketData, JpaIntelligencePipelineRunRepository runs,
            Clock clock, LiveStrategyEvaluationRunner strategyEvaluation,
            ShadowStrategyParityMonitor parity, StrategyMatchPersister matches,
            StrategyMatchOpportunityFactory matchOpportunities) {
        this.observations = observations;
        this.opportunities = opportunities;
        this.marketData = marketData;
        this.runs = runs;
        this.clock = clock;
        this.strategyEvaluation = strategyEvaluation;
        this.parity = parity;
        this.matches = matches;
        this.matchOpportunities = matchOpportunities;
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
        // Authoritative setup decision: the deterministic evaluator.
        var evaluation = strategyEvaluation.evaluateLegacyOhlcTrend(
                observation, marketId, clock.instant());
        parity.compareWithLegacyDecision(evaluation, observation, marketId);
        if (evaluation.status() != StrategyEvaluationStatus.MATCH) {
            if (evaluation.status() == StrategyEvaluationStatus.NO_MATCH) {
                run.noSignal(controlledNoSignalMessage(evaluation), clock.instant());
                return runs.save(run);
            }
            // NOT_EVALUABLE / FAILED keep their distinct truthful semantics.
            run.fail("STRATEGY_" + evaluation.status().name(),
                    evaluation.explanation() == null
                            ? "Strategy evaluation did not produce a usable outcome"
                            : evaluation.explanation(),
                    clock.instant());
            return runs.save(run);
        }
        // Required truth inside T1: match + opportunity commit or roll back together.
        var persisted = matches.persist(
                evaluation, analysisExecutionId, observation.id()).orElseThrow();
        CreateOpportunityCommand command = matchOpportunities.command(
                persisted.match(), instrument, originOf(mode),
                new ObservationReference(observation.id()),
                observation.validFrom(),
                observation.validFrom(),
                observation.validUntil().orElse(null));
        OpportunityCreationResult created = opportunities.create(command);
        TradingOpportunity analyzed = opportunities.transition(
                created.opportunity().id(), OpportunityStatus.ANALYZED);
        TradingOpportunity active = opportunities.transition(
                analyzed.id(), OpportunityStatus.ACTIVE);
        run.complete(observation.id(), observation.version(), active.id().value(),
                active.version().value(), clock.instant());
        return runs.save(run);
    }

    private static OpportunityOrigin originOf(AnalysisExecutionMode mode) {
        return mode == AnalysisExecutionMode.PASSIVE
                ? OpportunityOrigin.PASSIVE_SCAN : OpportunityOrigin.USER_REQUEST;
    }

    private String controlledNoSignalMessage(
            com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation evaluation) {
        return evaluation.explanation() == null
                ? "Strategy conditions not satisfied"
                : evaluation.explanation();
    }

    private String controlledMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }
}
