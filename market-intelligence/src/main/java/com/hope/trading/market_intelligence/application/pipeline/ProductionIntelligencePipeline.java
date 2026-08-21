package com.hope.trading.market_intelligence.application.pipeline;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.persistence.*;
import com.hope.trading.market_intelligence.application.observation.ObservationBuilder;
import com.hope.trading.market_intelligence.application.opportunity.*;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import com.hope.trading.market_intelligence.strategy.application.ShadowStrategyParityMonitor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductionIntelligencePipeline {
    public static final String VERSION = "production-intelligence/v1";
    private final ObservationBuilder observations;
    private final OpportunityEngine opportunities;
    private final MarketDataClient marketData;
    private final JpaIntelligencePipelineRunRepository runs;
    private final Clock clock;
    private final ShadowStrategyParityMonitor shadowParity;

    public ProductionIntelligencePipeline(
            ObservationBuilder observations, OpportunityEngine opportunities,
            MarketDataClient marketData, JpaIntelligencePipelineRunRepository runs,
            Clock clock, ShadowStrategyParityMonitor shadowParity) {
        this.observations = observations;
        this.opportunities = opportunities;
        this.marketData = marketData;
        this.runs = runs;
        this.clock = clock;
        this.shadowParity = shadowParity;
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
            // Story 0010 shadow mode: deterministic evaluator parity check.
            // Trader-facing behavior remains owned by the legacy rule above.
            shadowParity.compareWithLegacyDecision(
                    observation, marketId, analysisExecutionId, clock.instant());
        } catch (java.util.NoSuchElementException exception) {
            run.noSignal(exception.getMessage(), clock.instant());
            return runs.save(run);
        } catch (RuntimeException exception) {
            run.fail("OBSERVATION", controlledMessage(exception), clock.instant());
            return runs.save(run);
        }
        try {
            OpportunityDirection direction = observation.type().value().endsWith("LONG")
                    ? OpportunityDirection.LONG : OpportunityDirection.SHORT;
            OpportunityOrigin origin = mode == AnalysisExecutionMode.PASSIVE
                    ? OpportunityOrigin.PASSIVE_SCAN : OpportunityOrigin.USER_REQUEST;
            OpportunityCreationResult created = opportunities.create(new CreateOpportunityCommand(
                    instrument, direction, "OHLC_TREND", observation.horizon(), origin,
                    Set.of(new ObservationReference(observation.id())), Set.of(),
                    observation.validFrom(), observation.validUntil().orElse(null)));
            TradingOpportunity analyzed = opportunities.transition(
                    created.opportunity().id(), OpportunityStatus.ANALYZED);
            TradingOpportunity active = opportunities.transition(
                    analyzed.id(), OpportunityStatus.ACTIVE);
            run.complete(observation.id(), observation.version(), active.id().value(),
                    active.version().value(), clock.instant());
            return runs.save(run);
        } catch (RuntimeException exception) {
            run.fail("OPPORTUNITY", controlledMessage(exception), clock.instant());
            return runs.save(run);
        }
    }

    private String controlledMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }
}
