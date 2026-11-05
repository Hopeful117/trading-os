package com.hope.trading.market_intelligence.application.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.adapter.persistence.InMemoryResearchExecutionRepository;
import com.hope.trading.market_intelligence.application.execution.CapabilityAnalysisCoordinator;
import com.hope.trading.market_intelligence.application.capability.CapabilityAnalysisResult;
import com.hope.trading.market_intelligence.application.capability.HttpAiAnalysisCapability;
import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.application.port.RequestedAnalysis;
import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.research.ResearchExecutionStatus;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResearchApplicationServiceTest {
    private final UUID actor = UUID.randomUUID();
    private final UUID firstMarket = UUID.randomUUID();
    private final UUID secondMarket = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);
    private final MarketDataClient marketData = mock(MarketDataClient.class);
    private final MarketThesisRepository theses = mock(MarketThesisRepository.class);
    private final CapabilityAnalysisCoordinator analysis = mock(CapabilityAnalysisCoordinator.class);
    private final ResearchApplicationService service = new ResearchApplicationService(
            new InMemoryResearchExecutionRepository(), marketData, theses, analysis,
            new ObjectMapper().registerModule(new JavaTimeModule()), clock);

    @Test
    void rejectsNonPairScope() {
        assertThatThrownBy(() -> service.start(actor, "key",
                new ResearchApplicationService.ResearchRequest(List.of(firstMarket), "compare", "STANDARD")))
                .isInstanceOf(ResearchException.class)
                .hasMessageContaining("Exactly two distinct market IDs");
    }

    @Test
    void completesDeterministicResearchAndReplaysIdempotentRequest() {
        when(marketData.findMarket(firstMarket)).thenReturn(market(firstMarket, "BTC/USD"));
        when(marketData.findMarket(secondMarket)).thenReturn(market(secondMarket, "ETH/USD"));
        when(theses.findByMarket(any())).thenReturn(List.of());
        when(analysis.analyze(any(), any())).thenReturn(new ConsolidatedIntelligence(
                UUID.randomUUID(), firstMarket, AnalysisExecutionMode.PASSIVE,
                IntelligenceExecutionStatus.COMPLETE, List.of(), List.of(), List.of(), null));

        ResearchApplicationService.ResearchRequest request =
                new ResearchApplicationService.ResearchRequest(
                        List.of(firstMarket, secondMarket), "Compare BTC and ETH", "STANDARD");
        var first = service.start(actor, "key", request);
        var replay = service.start(actor, "key", request);

        assertThat(first.status()).as("failure code: %s", first.failureCode())
                .isEqualTo(ResearchExecutionStatus.COMPLETED);
        assertThat(first.report()).isNotNull();
        assertThat(replay.researchId()).isEqualTo(first.researchId());
        verify(analysis, times(2)).analyze(any(), any());
    }

    @Test
    void rejectsIdempotencyReuseWithDifferentRequest() {
        when(marketData.findMarket(firstMarket)).thenReturn(market(firstMarket, "BTC/USD"));
        when(marketData.findMarket(secondMarket)).thenReturn(market(secondMarket, "ETH/USD"));
        when(theses.findByMarket(any())).thenReturn(List.of());
        when(analysis.analyze(any(), any())).thenReturn(null);

        service.start(actor, "key", new ResearchApplicationService.ResearchRequest(
                List.of(firstMarket, secondMarket), "first", "STANDARD"));

        assertThatThrownBy(() -> service.start(actor, "key", new ResearchApplicationService.ResearchRequest(
                List.of(firstMarket, secondMarket), "second", "STANDARD")))
                .isInstanceOf(ResearchException.class)
                .hasMessageContaining("already bound");
    }

    @Test
    void recordsAiFailureWhenProviderReturnsWarningWithoutFindings() {
        HttpAiAnalysisCapability aiCapability = mock(HttpAiAnalysisCapability.class);
        ObjectProvider<HttpAiAnalysisCapability> aiProvider = mock(ObjectProvider.class);
        when(aiProvider.getIfAvailable()).thenReturn(aiCapability);
        when(aiCapability.analyzeAdaptive(any(), any()))
                .thenReturn(CapabilityAnalysisResult.empty("AI Engine execution timed out"));
        when(marketData.findMarket(firstMarket)).thenReturn(market(firstMarket, "BTC/USD"));
        when(marketData.findMarket(secondMarket)).thenReturn(market(secondMarket, "ETH/USD"));
        MarketThesis thesis = mock(MarketThesis.class);
        when(thesis.id()).thenReturn(UUID.randomUUID());
        when(thesis.version()).thenReturn(1L);
        when(thesis.validUntil()).thenReturn(java.util.Optional.empty());
        when(thesis.sourceCutoff()).thenReturn(Instant.parse("2026-11-05T11:00:00Z"));
        when(thesis.supportingEvidence()).thenReturn(List.of());
        when(thesis.contradictingEvidence()).thenReturn(List.of());
        when(thesis.missingEvidence()).thenReturn(List.of());
        when(thesis.scenario()).thenReturn(java.util.Optional.empty());
        when(theses.findByMarket(any())).thenReturn(List.of(thesis));
        when(analysis.analyze(any(), any())).thenReturn(new ConsolidatedIntelligence(
                UUID.randomUUID(), firstMarket, AnalysisExecutionMode.PASSIVE,
                IntelligenceExecutionStatus.COMPLETE, List.of(), List.of(), List.of(), null));

        ResearchApplicationService serviceWithAi = new ResearchApplicationService(
                new InMemoryResearchExecutionRepository(), marketData, theses, analysis,
                new ObjectMapper().registerModule(new JavaTimeModule()), clock, aiProvider);

        var execution = serviceWithAi.start(actor, "ai-timeout-key",
                new ResearchApplicationService.ResearchRequest(
                        List.of(firstMarket, secondMarket), "Compare BTC and ETH", "STANDARD"));

        assertThat(execution.status()).isEqualTo(ResearchExecutionStatus.COMPLETED);
        assertThat(execution.report().get("markets").get(0).get("aiStatus").asText())
                .isEqualTo("FAILED");
        assertThat(execution.report().get("markets").get(0).get("diagnostics").toString())
                .contains("AI_INTERPRETATION_WARNING");
    }

    @Test
    void enforcesOneAdaptiveFollowUpAcrossTheResearchExecution() {
        HttpAiAnalysisCapability aiCapability = mock(HttpAiAnalysisCapability.class);
        ObjectProvider<HttpAiAnalysisCapability> aiProvider = mock(ObjectProvider.class);
        when(aiProvider.getIfAvailable()).thenReturn(aiCapability);
        when(aiCapability.analyzeAdaptive(any(), any())).thenReturn(
                new CapabilityAnalysisResult(List.of(), List.of(), new RequestedAnalysis(
                        "market-structure-analysis", "1.1.0", firstMarket,
                        "Test market structure", "Structure levels")));
        when(marketData.findMarket(firstMarket)).thenReturn(market(firstMarket, "BTC/USD"));
        when(marketData.findMarket(secondMarket)).thenReturn(market(secondMarket, "ETH/USD"));
        MarketThesis thesis = thesis();
        when(theses.findByMarket(any())).thenReturn(List.of(thesis));
        when(analysis.analyze(any(), any())).thenReturn(new ConsolidatedIntelligence(
                UUID.randomUUID(), firstMarket, AnalysisExecutionMode.PASSIVE,
                IntelligenceExecutionStatus.COMPLETE, List.of(), List.of(), List.of(), null));
        when(analysis.analyze(any(), any(), any())).thenReturn(new ConsolidatedIntelligence(
                UUID.randomUUID(), firstMarket, AnalysisExecutionMode.PASSIVE,
                IntelligenceExecutionStatus.COMPLETE, List.of(), List.of(), List.of(), null));

        ResearchApplicationService serviceWithAi = new ResearchApplicationService(
            new InMemoryResearchExecutionRepository(), marketData, theses, analysis,
                new ObjectMapper().registerModule(new JavaTimeModule()), clock, aiProvider);

        var execution = serviceWithAi.start(actor, "adaptive-budget-key",
                new ResearchApplicationService.ResearchRequest(
                        List.of(firstMarket, secondMarket), "Compare BTC and ETH", "STANDARD"));

        assertThat(execution.report().get("executedCapabilities")).as("report=%s", execution.report()).hasSize(1);
        assertThat(execution.report().get("rejectedCapabilities")).hasSize(1);
        assertThat(execution.report().get("rejectedCapabilities").get(0).get("diagnostic").asText())
                .isEqualTo("ADAPTIVE_BUDGET_EXHAUSTED");
    }

    @Test
    void recordsMalformedAdaptiveRequestInsteadOfFailingTheWholeInterpretation() {
        HttpAiAnalysisCapability aiCapability = mock(HttpAiAnalysisCapability.class);
        ObjectProvider<HttpAiAnalysisCapability> aiProvider = mock(ObjectProvider.class);
        when(aiProvider.getIfAvailable()).thenReturn(aiCapability);
        when(aiCapability.analyzeAdaptive(any(), any())).thenReturn(
                new CapabilityAnalysisResult(List.of(), List.of(),
                        new RequestedAnalysis("", "", null, "", "")));
        when(marketData.findMarket(firstMarket)).thenReturn(market(firstMarket, "BTC/USD"));
        when(marketData.findMarket(secondMarket)).thenReturn(market(secondMarket, "ETH/USD"));
        MarketThesis marketThesis = thesis();
        when(theses.findByMarket(any())).thenReturn(List.of(marketThesis));
        when(analysis.analyze(any(), any())).thenReturn(new ConsolidatedIntelligence(
                UUID.randomUUID(), firstMarket, AnalysisExecutionMode.PASSIVE,
                IntelligenceExecutionStatus.COMPLETE, List.of(), List.of(), List.of(), null));

        ResearchApplicationService serviceWithAi = new ResearchApplicationService(
                new InMemoryResearchExecutionRepository(), marketData, theses, analysis,
                new ObjectMapper().registerModule(new JavaTimeModule()), clock, aiProvider);

        var execution = serviceWithAi.start(actor, "malformed-adaptive-key",
                new ResearchApplicationService.ResearchRequest(
                        List.of(firstMarket, secondMarket), "Compare BTC and ETH", "STANDARD"));

        assertThat(execution.report().get("rejectedCapabilities")).hasSize(2);
        assertThat(execution.report().get("rejectedCapabilities").get(0).get("diagnostic").asText())
                .isEqualTo("ADAPTIVE_REQUEST_MALFORMED");
        assertThat(execution.report().get("markets").get(0).get("aiStatus").asText())
                .isEqualTo("PARTIAL");
    }

    private MarketThesis thesis() {
        MarketThesis thesis = mock(MarketThesis.class);
        when(thesis.id()).thenReturn(UUID.randomUUID());
        when(thesis.version()).thenReturn(1L);
        when(thesis.validUntil()).thenReturn(java.util.Optional.empty());
        when(thesis.sourceCutoff()).thenReturn(Instant.parse("2026-11-05T11:00:00Z"));
        when(thesis.supportingEvidence()).thenReturn(List.of());
        when(thesis.contradictingEvidence()).thenReturn(List.of());
        when(thesis.missingEvidence()).thenReturn(List.of());
        when(thesis.scenario()).thenReturn(java.util.Optional.empty());
        return thesis;
    }

    private MarketResponse market(UUID id, String symbol) {
        return new MarketResponse(id, "KRAKEN", symbol, symbol.substring(0, 3), "USD", "SPOT", null);
    }
}
