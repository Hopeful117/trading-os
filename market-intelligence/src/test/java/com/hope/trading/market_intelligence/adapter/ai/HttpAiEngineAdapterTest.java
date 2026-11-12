package com.hope.trading.market_intelligence.adapter.ai;

import com.hope.trading.market_intelligence.domain.FindingType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpAiEngineAdapterTest {
    @Test
    void mapsValidatedInterpretationToAiFindingsWithoutCreatingDomainDecisions() {
        AiEngineClient client = mock(AiEngineClient.class);
        UUID executionId = UUID.randomUUID();
        Instant acceptedAt = Instant.parse("2026-11-05T12:00:00Z");
        when(client.result(executionId)).thenReturn(new AiEngineResultResponse(
                executionId, "COMPLETED", "COMPLETE",
                new InterpretationWire(
                        "Summary", "Scenario",
                        List.of(new ArgumentWire("Support", List.of())),
                        List.of(new ArgumentWire("Contradiction", List.of())),
                        List.of("Missing range data"), List.of("Confirm reclaim"),
                        List.of("Invalidate below level"), List.of("Limited history"),
                        "REVIEW_CANDIDATE"),
                 null, null, "mock", "fixture", acceptedAt, acceptedAt,
                 new AiProvenanceWire(executionId.toString(), "mock", "fixture", "MOCK", true,
                         acceptedAt, acceptedAt, null, false, null, "ai-engine-result-v1")));

        var result = new HttpAiEngineAdapter(client, 256_000, 1_500)
                .result(new com.hope.trading.market_intelligence.application.port.AiExecutionReference(
                        executionId.toString()))
                .orElseThrow();

        assertThat(result.acceptedAt()).isEqualTo(acceptedAt);
        assertThat(result.provenance().classification()).isEqualTo("MOCK");
        assertThat(result.provenance().executionId()).isEqualTo(executionId.toString());
        assertThat(result.result().findings()).hasSize(9);
        assertThat(result.result().findings())
                .extracting(value -> value.type())
                .contains(FindingType.AI_FINDING, FindingType.SCENARIO, FindingType.WARNING);
        assertThat(result.result().findings()).allMatch(value ->
                value.origin() == com.hope.trading.market_intelligence.domain.AnalysisOrigin.AI);
    }
}
