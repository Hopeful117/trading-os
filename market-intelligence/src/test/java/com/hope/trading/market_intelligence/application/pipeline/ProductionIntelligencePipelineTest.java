package com.hope.trading.market_intelligence.application.pipeline;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.persistence.JpaIntelligencePipelineRunEntity;
import com.hope.trading.market_intelligence.adapter.persistence.JpaIntelligencePipelineRunRepository;
import com.hope.trading.market_intelligence.application.observation.ObservationBuilder;
import com.hope.trading.market_intelligence.application.opportunity.CreateOpportunityCommand;
import com.hope.trading.market_intelligence.application.opportunity.OpportunityCreationResult;
import com.hope.trading.market_intelligence.application.opportunity.OpportunityEngine;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityStatus;
import com.hope.trading.market_intelligence.application.opportunity.StrategyMatchOpportunityFactory;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import com.hope.trading.market_intelligence.strategy.application.LiveStrategyEvaluationRunner;
import com.hope.trading.market_intelligence.strategy.application.ShadowStrategyParityMonitor;
import com.hope.trading.market_intelligence.strategy.application.StrategyDefinitionRepository;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchPersister;
import com.hope.trading.market_intelligence.strategy.domain.ConditionResult;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationContext;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;
import com.hope.trading.market_intelligence.strategy.domain.StrategyOperationalStatus;
import com.hope.trading.market_intelligence.strategy.domain.StrategyParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STORY-0020A: protects the central orchestration semantics that were
 * previously almost untested (pipeline package ~12% line coverage).
 *
 * <p>Behaviors protected:</p>
 * <ul>
 *   <li>market-data failure fails the run visibly (OBSERVATION stage);</li>
 *   <li>observation absence is a truthful NO_SIGNAL, not a failure;</li>
 *   <li>a strategy MATCH persists a match, creates an opportunity and
 *       completes the run with its singular id;</li>
 *   <li>multiple matches complete the run WITHOUT a singular opportunity id
 *       (no hidden first-pick);</li>
 *   <li>ineligible strategies are simply not evaluated (governance gating);</li>
 *   <li>runs are idempotent per (analysis execution, pipeline version).</li>
 * </ul>
 *
 * <p>Strategy-agnostic by construction: collaborators are mocked at their
 * boundaries and fixtures carry no concrete StrategyId branching.</p>
 */
class ProductionIntelligencePipelineTest {

    private final ObservationBuilder observations = mock(ObservationBuilder.class);
    private final OpportunityEngine opportunities = mock(OpportunityEngine.class);
    private final MarketDataClient marketData = mock(MarketDataClient.class);
    private final JpaIntelligencePipelineRunRepository runs =
            mock(JpaIntelligencePipelineRunRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-23T10:00:00Z"),
            ZoneOffset.UTC);
    private final LiveStrategyEvaluationRunner evaluationRunner =
            mock(LiveStrategyEvaluationRunner.class);
    private final ShadowStrategyParityMonitor parity = mock(ShadowStrategyParityMonitor.class);
    private final StrategyMatchPersister matchPersister = mock(StrategyMatchPersister.class);
    private final StrategyMatchOpportunityFactory matchOpportunities =
            mock(StrategyMatchOpportunityFactory.class);
    private final StrategyDefinitionRepository definitions =
            mock(StrategyDefinitionRepository.class);

    private ProductionIntelligencePipeline pipeline;

    private final UUID analysisExecutionId = UUID.randomUUID();
    private final UUID marketId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        pipeline = new ProductionIntelligencePipeline(observations, opportunities,
                marketData, runs, clock, evaluationRunner, parity, matchPersister,
                matchOpportunities, definitions);
        when(runs.findByAnalysisExecutionIdAndPipelineVersion(
                any(UUID.class), anyString())).thenReturn(Optional.empty());
        when(runs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var marketResponse = new com.hope.trading.market_intelligence.adapter.marketdata
                .MarketResponse(marketId, "KRAKEN", "BTC/EUR", "BTC", "EUR", null);
        when(marketData.findMarket(marketId)).thenReturn(marketResponse);

        Observation observation = mock(Observation.class);
        when(observation.id()).thenReturn(UUID.randomUUID());
        when(observation.version()).thenReturn(1L);
        when(observation.instrument()).thenReturn("BTC/EUR");
        when(observation.validFrom()).thenReturn(clock.instant());
        when(observation.validUntil()).thenReturn(Optional.of(clock.instant()
                .plusSeconds(300)));
        when(observation.horizon()).thenReturn("15m");
        when(observations.build(any(UUID.class), anyString(), any())).thenReturn(observation);
    }

    /** Eligible (VALIDATED + ENABLED), M15/CRYPTO/KRAKEN-applicable definition. */
    private static StrategyDefinition enabledStrategy(String name) {
        return StrategyDefinition.create(
                        new StrategyId(UUID.randomUUID()), 1, name, null, name,
                        StrategyDirection.DYNAMIC,
                        new StrategyApplicability(java.util.Set.of("CRYPTO"),
                                java.util.Set.of(StrategyApplicability.Timeframe.M15),
                                java.util.Set.of()),
                        Set.of(), StrategyParameters.empty(), null, Instant.EPOCH)
                .recordValidation("backtest://fixture", Instant.EPOCH)
                .transitionTo(StrategyOperationalStatus.ENABLED, Instant.EPOCH);
    }

    private void stubMatch(StrategyDefinition definition) {
        StrategyEvaluationContext context = StrategyEvaluationContext.builder()
                .marketId(marketId).instrument("BTC/EUR")
                .timeframe(StrategyApplicability.Timeframe.M15)
                .evaluatedAt(clock.instant()).build();
        when(evaluationRunner.evaluate(any(StrategyDefinition.class),
                any(Observation.class), any(UUID.class), any(Instant.class)))
                .thenAnswer(inv -> StrategyEvaluation.match(
                        inv.getArgument(0), context, MatchedDirection.LONG,
                        List.of(ConditionResult.of("condition", true, BigDecimal.ONE)),
                        BigDecimal.ONE, "test setup", Set.of()));

        StrategyMatch match = StrategyMatch.fromEvaluation(
                StrategyEvaluation.match(
                        definition, context, MatchedDirection.LONG, List.of(),
                        BigDecimal.ONE, "test setup", Set.of()),
                analysisExecutionId, UUID.randomUUID(), UUID.randomUUID(),
                clock.instant());
        when(matchPersister.persist(any(), any(UUID.class), any(UUID.class)))
                .thenReturn(Optional.of(new com.hope.trading.market_intelligence
                        .strategy.application.StrategyMatchPersistResult(match, true)));

        TradingOpportunity opportunity = mock(TradingOpportunity.class);
        when(opportunity.id()).thenReturn(new OpportunityId(UUID.randomUUID()));
        when(opportunity.version()).thenReturn(
                new com.hope.trading.market_intelligence.domain.opportunity
                        .OpportunityVersion(1));
        when(opportunities.create(any(CreateOpportunityCommand.class)))
                .thenReturn(new OpportunityCreationResult.Created(opportunity));
        when(opportunities.transition(any(OpportunityId.class), any(OpportunityStatus.class)))
                .thenReturn(opportunity);
        when(matchOpportunities.command(any(), any(StrategyDefinition.class),
                anyString(), any(), any(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(mock(CreateOpportunityCommand.class));
    }

    @Test
    void marketDataFailureFailsTheRunAtObservationStage() {
        when(marketData.findMarket(marketId))
                .thenThrow(new IllegalStateException("market data down"));

        JpaIntelligencePipelineRunEntity run =
                pipeline.process(analysisExecutionId, marketId, AnalysisExecutionMode.PASSIVE);

        assertThat(run.state()).isEqualTo("FAILED_OBSERVATION");
        verify(observations, never()).build(any(), anyString(), any());
    }

    @Test
    void missingObservationIsTruthfulNoSignalNotFailure() {
        when(observations.build(any(UUID.class), anyString(), any()))
                .thenThrow(new java.util.NoSuchElementException("No complete OHLC range"));

        JpaIntelligencePipelineRunEntity run =
                pipeline.process(analysisExecutionId, marketId, AnalysisExecutionMode.PASSIVE);

        assertThat(run.state()).isEqualTo("COMPLETED_NO_SIGNAL");
    }

    @Test
    void singleMatchCompletesRunWithThatOpportunityId() {
        StrategyDefinition definition = enabledStrategy("Only Strategy");
        when(definitions.findAll()).thenReturn(List.of(definition));
        stubMatch(definition);

        JpaIntelligencePipelineRunEntity run =
                pipeline.process(analysisExecutionId, marketId, AnalysisExecutionMode.PASSIVE);

        assertThat(run.state()).isEqualTo("COMPLETED");
        assertThat(run.opportunityId()).isNotNull();
        verify(matchPersister).persist(any(), any(UUID.class), any(UUID.class));
        verify(opportunities).create(any());
    }

    @Test
    void multipleMatchesCompleteRunWithoutSingularOpportunityId() {
        StrategyDefinition first = enabledStrategy("First Strategy");
        StrategyDefinition second = enabledStrategy("Second Strategy");
        when(definitions.findAll()).thenReturn(List.of(first, second));
        stubMatch(first);
        stubMatch(second);

        JpaIntelligencePipelineRunEntity run =
                pipeline.process(analysisExecutionId, marketId, AnalysisExecutionMode.PASSIVE);

        assertThat(run.state()).isEqualTo("COMPLETED");
        // No hidden first-pick: the singular projection is not representable.
        assertThat(run.opportunityId()).isNull();
        verify(opportunities, org.mockito.Mockito.times(2)).create(any());
    }

    @Test
    void ineligibleStrategiesAreNeverEvaluated() {
        // Built-in default state of create(): UNVALIDATED + DISABLED.
        StrategyDefinition disabled = StrategyDefinition.create(
                new StrategyId(UUID.randomUUID()), 1, "Disabled Strategy", null, "DISABLED",
                StrategyDirection.DYNAMIC,
                new StrategyApplicability(Set.of("CRYPTO"),
                        Set.of(StrategyApplicability.Timeframe.M15), Set.of()),
                Set.of(), StrategyParameters.empty(), null, Instant.EPOCH);
        when(definitions.findAll()).thenReturn(List.of(disabled));

        JpaIntelligencePipelineRunEntity run =
                pipeline.process(analysisExecutionId, marketId, AnalysisExecutionMode.PASSIVE);

        assertThat(run.state()).isEqualTo("COMPLETED_NO_SIGNAL");
        verify(evaluationRunner, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void runsAreIdempotentPerAnalysisExecutionAndPipelineVersion() {
        JpaIntelligencePipelineRunEntity existing =
                JpaIntelligencePipelineRunEntity.running(
                        analysisExecutionId, ProductionIntelligencePipeline.VERSION,
                        clock.instant());
        existing.complete(UUID.randomUUID(), 1L, UUID.randomUUID(), 1L, clock.instant());
        when(runs.findByAnalysisExecutionIdAndPipelineVersion(
                analysisExecutionId, ProductionIntelligencePipeline.VERSION))
                .thenReturn(Optional.of(existing));

        JpaIntelligencePipelineRunEntity run =
                pipeline.process(analysisExecutionId, marketId, AnalysisExecutionMode.PASSIVE);

        assertThat(run).isSameAs(existing);
        verify(marketData, never()).findMarket(any());
    }
}
