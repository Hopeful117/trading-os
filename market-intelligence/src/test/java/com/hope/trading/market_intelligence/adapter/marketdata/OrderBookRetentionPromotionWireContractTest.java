package com.hope.trading.market_intelligence.adapter.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookRetentionPromotionWireContractTest {
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void preservesReplayProvenanceInPromotionPayload() throws Exception {
        UUID marketId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID sequenceId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Instant cutoff = Instant.parse("2026-10-16T10:00:00Z");
        OrderBookReplayClient.ReplayResponse replay = new OrderBookReplayClient.ReplayResponse(
                "AVAILABLE", marketId, 25, sequenceId, cutoff, cutoff, cutoff,
                10L, 11L, "KRAKEN", "PF_XBTUSD", "order-book-normalization-v1",
                "source-fingerprint", "TRUSTED", List.of("source-fingerprint"), List.of(), List.of(), null);
        OrderBookRetentionClient.PromotionRequest request = new OrderBookRetentionClient.PromotionRequest(
                replay, "order-book-replay:v1:market=" + marketId, "research-lifecycle:v1");

        JsonNode payload = json.readTree(json.writeValueAsString(request));
        assertThat(payload.path("semanticIdentity").asText()).isEqualTo(request.semanticIdentity());
        assertThat(payload.path("promotionReason").asText()).isEqualTo("research-lifecycle:v1");
        assertThat(payload.path("replay").path("marketId").asText()).isEqualTo(marketId.toString());
        assertThat(payload.path("replay").path("evaluationCutoff").asText()).isEqualTo(cutoff.toString());
        assertThat(payload.path("replay").path("sourceTrustStatus").asText()).isEqualTo("TRUSTED");

        OrderBookRetentionClient.PromotionRequest decoded = json.readValue(
                payload.toString(), OrderBookRetentionClient.PromotionRequest.class);
        assertThat(decoded.replay().sequenceId()).isEqualTo(sequenceId);
        assertThat(decoded.replay().sourceEvidenceFingerprints()).containsExactly("source-fingerprint");
    }
}
