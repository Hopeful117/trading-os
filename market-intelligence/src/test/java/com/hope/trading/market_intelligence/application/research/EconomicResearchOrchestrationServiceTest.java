package com.hope.trading.market_intelligence.application.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.adapter.tradingcore.EconomicResearchClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EconomicResearchOrchestrationServiceTest {
    @Test
    void enforcesPopulationBoundBeforeSnapshotResolutionOrRemoteEvaluation() {
        var snapshots = mock(ExactFeatureSnapshotResolver.class);
        var tradingCore = mock(EconomicResearchClient.class);
        var service = new EconomicResearchOrchestrationService(new ObjectMapper(), snapshots, tradingCore);

        String oversizedPopulation = String.join("\n", java.util.Collections.nCopies(1_001, "{}"));

        assertThatThrownBy(() -> service.evaluate(oversizedPopulation, Instant.parse("2026-10-17T10:00:00Z"), "cost-v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configured bound");
        verifyNoInteractions(snapshots, tradingCore);
    }

    @Test
    void resolvesCandidatesBeforeDelegatingTheNormalizedBoundedRequest() throws Exception {
        var snapshots = mock(ExactFeatureSnapshotResolver.class);
        var tradingCore = mock(EconomicResearchClient.class);
        var expected = new EconomicResearchClient.EvaluationResponse("candidates", "results", "manifest", "readme", "fingerprint");
        when(tradingCore.evaluate(any())).thenReturn(expected);
        var service = new EconomicResearchOrchestrationService(new ObjectMapper(), snapshots, tradingCore);
        String candidate = "{\"featureSnapshot\":{\"marketId\":\"00000000-0000-0000-0000-000000000001\",\"evaluationCutoff\":\"2026-10-17T10:00:00Z\",\"featureProjectionVersion\":\"feature-v1\",\"contextFingerprint\":\"context\",\"featureSnapshotFingerprint\":\"snapshot\",\"identityFingerprint\":\"identity\"}}";

        service.evaluate(candidate, Instant.parse("2026-10-17T10:00:00Z"), "cost-v1");

        verify(snapshots).resolve(any(), any(), eq("feature-v1"), eq("context"), eq("snapshot"), eq("identity"));
        verify(tradingCore).evaluate(new EconomicResearchClient.EvaluationRequest(candidate,
                "2026-10-17T10:00:00Z", "cost-v1"));
    }
}
