package com.hope.trading.market_intelligence.application.pipeline;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketPriceSnapshotResponse;
import com.hope.trading.market_intelligence.adapter.persistence.JpaAnalysisTradePlanGenerationEntity;
import com.hope.trading.market_intelligence.adapter.persistence.JpaAnalysisTradePlanGenerationRepository;
import com.hope.trading.market_intelligence.adapter.persistence.JpaIntelligencePipelineRunEntity;
import com.hope.trading.market_intelligence.adapter.persistence.JpaIntelligencePipelineRunRepository;
import com.hope.trading.market_intelligence.adapter.persistence.JpaAnalysisTradePlanGenerationClaimService;
import com.hope.trading.market_intelligence.adapter.web.InternalAnalysisTradePlanRequest;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.TradePlanningContextRepository;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanApplicationService;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionProvenance;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.application.opportunity.OpportunityTestFixtures;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityStatus;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityScore;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlan;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanningRequest;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanningResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnalysisTradePlanGenerationServiceTest {
    @Test
    void rejectsCompletedPipelineWithoutSingularOpportunitySelection() {
        UUID analysisId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        AnalysisExecutionRepository analyses = mock(AnalysisExecutionRepository.class);
        AnalysisExecution analysis = mock(AnalysisExecution.class);
        when(analysis.status()).thenReturn(AnalysisExecutionStatus.COMPLETED);
        when(analyses.findById(analysisId)).thenReturn(Optional.of(analysis));

        JpaIntelligencePipelineRunRepository pipelineRuns = mock(JpaIntelligencePipelineRunRepository.class);
        JpaIntelligencePipelineRunEntity pipeline = JpaIntelligencePipelineRunEntity.running(
                analysisId, ProductionIntelligencePipeline.VERSION, now);
        pipeline.complete(UUID.randomUUID(), 1, null, 0, now);
        when(pipelineRuns.findByAnalysisExecutionIdAndPipelineVersion(
                analysisId, ProductionIntelligencePipeline.VERSION)).thenReturn(Optional.of(pipeline));

        TradingOpportunityRepository opportunities = mock(TradingOpportunityRepository.class);
        JpaAnalysisTradePlanGenerationRepository generations = mock(JpaAnalysisTradePlanGenerationRepository.class);
        JpaAnalysisTradePlanGenerationClaimService generationClaims =
                mock(JpaAnalysisTradePlanGenerationClaimService.class);
        when(generationClaims.create(any(), any(), any(), any(), anyLong(), anyString(), any()))
                .thenAnswer(invocation -> JpaAnalysisTradePlanGenerationEntity.running(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                        invocation.getArgument(3), invocation.getArgument(4), invocation.getArgument(5),
                        invocation.getArgument(6)));
        when(generations.save(any(JpaAnalysisTradePlanGenerationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID contextId = UUID.randomUUID();
        InternalAnalysisTradePlanRequest request = request(actorId, accountId, contextId);
        AnalysisTradePlanGenerationService service = new AnalysisTradePlanGenerationService(
                analyses, pipelineRuns, opportunities,
                mock(TradePlanningContextRepository.class), mock(MarketDataClient.class),
                mock(TradePlanApplicationService.class), generations, generationClaims,
                Clock.fixed(now, ZoneOffset.UTC), java.time.Duration.ofSeconds(30));

        assertThatThrownBy(() -> service.generate(analysisId, "multi-opportunity", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("OPPORTUNITY_SELECTION_REQUIRED");
        verifyNoInteractions(opportunities);

        assertThatThrownBy(() -> service.generate(analysisId, " ", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    void forwardsPipelineOpportunityVersionIntoTradePlanningRequest() {
        UUID analysisId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        UUID opportunityId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        AnalysisExecution analysis = mock(AnalysisExecution.class);
        when(analysis.status()).thenReturn(AnalysisExecutionStatus.COMPLETED);
        when(analysis.provenance()).thenReturn(new AnalysisExecutionProvenance(
                marketId, AnalysisExecutionMode.ACTIVE, "test", "test-v1"));
        var pipeline = JpaIntelligencePipelineRunEntity.running(
                analysisId, ProductionIntelligencePipeline.VERSION, now);
        pipeline.complete(UUID.randomUUID(), 1, opportunityId, 3, now);
        TradingOpportunityRepository opportunities = mock(TradingOpportunityRepository.class);
        when(opportunities.find(new OpportunityId(opportunityId), new OpportunityVersion(3)))
                .thenReturn(Optional.of(OpportunityTestFixtures.opportunity(
                        new OpportunityId(opportunityId), 3, OpportunityStatus.ACTIVE,
                        new OpportunityScore(BigDecimal.TEN), now)));
        TradePlan plan = mock(TradePlan.class);
        when(plan.id()).thenReturn(new com.hope.trading.market_intelligence.domain.tradeplan.TradePlanId(UUID.randomUUID()));
        when(plan.version()).thenReturn(new com.hope.trading.market_intelligence.domain.tradeplan.TradePlanVersion(1));
        TradePlanApplicationService tradePlans = mock(TradePlanApplicationService.class);
        ArgumentCaptor<TradePlanningRequest> planningRequest = ArgumentCaptor.forClass(TradePlanningRequest.class);
        when(tradePlans.create(planningRequest.capture()))
                .thenReturn(new TradePlanningResult.Success(plan, List.of()));
        JpaAnalysisTradePlanGenerationRepository generations = mock(JpaAnalysisTradePlanGenerationRepository.class);
        JpaAnalysisTradePlanGenerationClaimService claims = mock(JpaAnalysisTradePlanGenerationClaimService.class);
        when(claims.create(any(), any(), any(), any(), anyLong(), anyString(), any()))
                .thenAnswer(invocation -> JpaAnalysisTradePlanGenerationEntity.running(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                        invocation.getArgument(3), invocation.getArgument(4), invocation.getArgument(5),
                        invocation.getArgument(6)));
        when(generations.findByAnalysisExecutionIdAndActorIdAndAccountIdAndIdempotencyKey(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(generations.save(any(JpaAnalysisTradePlanGenerationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AnalysisExecutionRepository analyses = mock(AnalysisExecutionRepository.class);
        when(analyses.findById(analysisId)).thenReturn(Optional.of(analysis));
        JpaIntelligencePipelineRunRepository pipelineRuns = mock(JpaIntelligencePipelineRunRepository.class);
        when(pipelineRuns.findByAnalysisExecutionIdAndPipelineVersion(
                analysisId, ProductionIntelligencePipeline.VERSION)).thenReturn(Optional.of(pipeline));
        TradePlanningContextRepository contexts = mock(TradePlanningContextRepository.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(marketData.findPriceSnapshots(any())).thenReturn(List.of(new MarketPriceSnapshotResponse(
                marketId, "BTC/EUR", BigDecimal.valueOf(101), BigDecimal.valueOf(100),
                BigDecimal.valueOf(102), true, now, "FRESH", "snapshot-1", 1L, now)));
        var service = new AnalysisTradePlanGenerationService(
                analyses, pipelineRuns, opportunities, contexts, marketData,
                tradePlans, generations, claims, Clock.fixed(now, ZoneOffset.UTC),
                java.time.Duration.ofSeconds(30));

        service.generate(analysisId, "exact-version", request(actorId, accountId, UUID.randomUUID()));

        assertThat(planningRequest.getValue().exactOpportunityVersions())
                .containsEntry(new OpportunityId(opportunityId), new OpportunityVersion(3));
    }

    private static InternalAnalysisTradePlanRequest request(UUID actorId, UUID accountId, UUID contextId) {
        return new InternalAnalysisTradePlanRequest(actorId, accountId,
                new InternalAnalysisTradePlanRequest.Context(
                        contextId, 1, Instant.parse("2026-01-01T00:00:00Z"), actorId, accountId,
                        "USD",
                        new InternalAnalysisTradePlanRequest.RiskBudget(
                                BigDecimal.valueOf(100), "USD", UUID.randomUUID(), 1),
                        new InternalAnalysisTradePlanRequest.Preferences(
                                UUID.randomUUID(), 1, "MARKET", "PERCENTAGE_DISTANCE",
                                BigDecimal.ONE, "RISK_MULTIPLE", BigDecimal.TWO,
                                "INTRADAY", java.time.Duration.ofHours(1))));
    }
}
