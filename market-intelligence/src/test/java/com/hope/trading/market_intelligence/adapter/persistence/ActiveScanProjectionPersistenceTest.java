package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.opportunity.OpportunityTestFixtures;
import com.hope.trading.market_intelligence.application.pipeline.ProductionIntelligencePipeline;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.AnalysisPipelineRunViewRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityVersionRef;
import com.hope.trading.market_intelligence.domain.execution.*;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import com.hope.trading.market_intelligence.strategy.application.BuiltinStrategies;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ActiveScanProjectionPersistenceTest {
    @Autowired AnalysisExecutionRepository executions;
    @Autowired AnalysisPipelineRunViewRepository pipelineRuns;
    @Autowired TradingOpportunityRepository opportunities;
    @Autowired StrategyMatchRepository strategyMatches;
    @Autowired JpaIntelligencePipelineRunRepository jpaPipelineRuns;

    @Test
    void batchLoadsAnalysisExecutionsById() {
        Instant now = Instant.parse("2026-08-21T10:00:00Z");
        AnalysisExecution first = requestedExecution(UUID.randomUUID(), UUID.randomUUID(), "key-1", now);
        AnalysisExecution second = requestedExecution(UUID.randomUUID(), UUID.randomUUID(), "key-2", now);
        executions.save(first);
        executions.save(second);

        assertThat(executions.findAllById(List.of(first.executionId(), second.executionId())))
                .extracting(AnalysisExecution::executionId)
                .containsExactlyInAnyOrder(first.executionId(), second.executionId());
    }

    @Test
    void loadsPipelineRunsForMultipleAnalysisExecutions() {
        Instant now = Instant.parse("2026-08-21T10:00:00Z");
        UUID firstExecutionId = UUID.randomUUID();
        UUID secondExecutionId = UUID.randomUUID();

        JpaIntelligencePipelineRunEntity first = JpaIntelligencePipelineRunEntity.running(
                firstExecutionId,
                ProductionIntelligencePipeline.VERSION,
                now
        );
        first.noSignal("No signal", now.plusSeconds(1));
        jpaPipelineRuns.saveAndFlush(first);

        JpaIntelligencePipelineRunEntity second = JpaIntelligencePipelineRunEntity.running(
                secondExecutionId,
                ProductionIntelligencePipeline.VERSION,
                now
        );
        second.fail("OPPORTUNITY", "Fusion failed", now.plusSeconds(1));
        jpaPipelineRuns.saveAndFlush(second);

        assertThat(pipelineRuns.findByAnalysisExecutionIdsAndPipelineVersion(
                List.of(firstExecutionId, secondExecutionId),
                ProductionIntelligencePipeline.VERSION
        )).extracting(run -> run.state() + ":" + run.failureCode())
                .containsExactlyInAnyOrder(
                        "COMPLETED_NO_SIGNAL:NO_SIGNAL",
                        "FAILED_OPPORTUNITY:PIPELINE_OPPORTUNITY_FAILED"
                );
    }

    @Test
    void loadsExactOpportunitiesByVersionedReference() {
        TradingOpportunity first = OpportunityTestFixtures.opportunity(
                new OpportunityId(UUID.randomUUID()),
                1,
                OpportunityStatus.ACTIVE,
                new OpportunityScore(new BigDecimal("71.25")),
                OpportunityTestFixtures.NOW
        );
        TradingOpportunity second = OpportunityTestFixtures.opportunity(
                new OpportunityId(UUID.randomUUID()),
                1,
                OpportunityStatus.ACTIVE,
                new OpportunityScore(new BigDecimal("84.50")),
                OpportunityTestFixtures.NOW.plusSeconds(1)
        );
        opportunities.append(first);
        opportunities.append(second);

        assertThat(opportunities.findAllExact(Set.of(
                new TradingOpportunityVersionRef(first.id(), first.version()),
                new TradingOpportunityVersionRef(second.id(), second.version())
        ))).extracting(opportunity -> opportunity.score().value())
                .containsExactlyInAnyOrder(
                        new BigDecimal("71.25").setScale(2),
                        new BigDecimal("84.50").setScale(2)
                );
    }

    @Test
    void loadsLatestOpportunityVersionsByStrategyMatchIds() {
        UUID strategyMatchId = UUID.randomUUID();
        UUID unrelatedStrategyMatchId = UUID.randomUUID();
        strategyMatches.save(strategyMatch(strategyMatchId));
        strategyMatches.save(strategyMatch(unrelatedStrategyMatchId));
        OpportunityId opportunityId = new OpportunityId(UUID.randomUUID());
        TradingOpportunity firstVersion = opportunity(
                opportunityId, 1, strategyMatchId, OpportunityStatus.ACTIVE, "71.25");
        TradingOpportunity latestVersion = opportunity(
                opportunityId, 2, strategyMatchId, OpportunityStatus.ANALYZED, "84.50");
        TradingOpportunity unrelated = opportunity(
                new OpportunityId(UUID.randomUUID()), 1, unrelatedStrategyMatchId,
                OpportunityStatus.ACTIVE, "92.00");
        opportunities.append(firstVersion);
        opportunities.append(latestVersion);
        opportunities.append(unrelated);

        assertThat(opportunities.findByStrategyMatchIds(List.of(strategyMatchId)))
                .singleElement()
                .satisfies(value -> {
                    assertThat(value.id()).isEqualTo(opportunityId);
                    assertThat(value.version()).isEqualTo(new OpportunityVersion(2));
                    assertThat(value.score().value()).isEqualByComparingTo("84.50");
                });
    }

    private StrategyMatch strategyMatch(UUID matchId) {
        Instant now = OpportunityTestFixtures.NOW;
        return StrategyMatch.rehydrate(
                matchId,
                new StrategyId(BuiltinStrategies.LEGACY_OHLC_TREND_ID),
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                MatchedDirection.LONG,
                "digest-" + matchId,
                List.of(),
                now,
                now
        );
    }

    private TradingOpportunity opportunity(
            OpportunityId id,
            long version,
            UUID strategyMatchId,
            OpportunityStatus status,
            String score
    ) {
        Instant now = OpportunityTestFixtures.NOW;
        return new OpportunityFactory().create(
                id,
                new OpportunityVersion(version),
                status,
                "BTC/EUR",
                OpportunityDirection.LONG,
                "Bullish breakout",
                "5m",
                OpportunityType.SCALPING,
                OpportunityOrigin.PASSIVE_SCAN,
                new OpportunityScore(new BigDecimal(score)),
                "Confirmed",
                Set.of(new ObservationReference(UUID.randomUUID())),
                Set.of(),
                now,
                now,
                now.plusSeconds(300),
                now,
                strategyMatchId,
                null
        );
    }

    private AnalysisExecution requestedExecution(
            UUID executionId,
            UUID marketId,
            String key,
            Instant now
    ) {
        return AnalysisExecution.requested(
                executionId,
                new IdempotencyKey(key),
                ExecutionTestFixtures.policy(),
                now,
                List.of("deterministic-active"),
                new AnalysisExecutionProvenance(marketId, com.hope.trading.market_intelligence.domain.AnalysisExecutionMode.ACTIVE, "scan", "v1"),
                AnalysisTraceMetadata.empty()
        );
    }
}
