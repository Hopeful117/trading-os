package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.OrderBookEvidenceOrigin;
import com.hope.trading.market_data.model.OrderBookLevel;
import com.hope.trading.market_data.model.OrderBookReplayArtifact;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookReplayArtifactFingerprintTest {
    private static final String SIGNING_KEY = "test-signing-key";
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final UUID SEQUENCE_ID = UUID.randomUUID();
    private static final Instant CUTOFF = Instant.parse("2026-10-20T12:00:00Z");

    @Test
    void matchesLegacyV2ArtifactWithoutOriginInFingerprintMaterial() {
        OrderBookReplayArtifact unsigned = artifact("order-book-replay-artifact-v2", OrderBookEvidenceOrigin.LOCAL_OBSERVATION,
                "placeholder");
        OrderBookReplayArtifact signed = withFingerprint(unsigned, OrderBookReplayArtifactFingerprint.calculate(unsigned, SIGNING_KEY));

        assertThat(OrderBookReplayArtifactFingerprint.matches(signed, SIGNING_KEY)).isTrue();
    }

    @Test
    void matchesV3ArtifactWithOriginInFingerprintMaterial() {
        OrderBookReplayArtifact unsigned = artifact("order-book-replay-artifact-v3", OrderBookEvidenceOrigin.MIXED,
                "placeholder");
        OrderBookReplayArtifact signed = withFingerprint(unsigned, OrderBookReplayArtifactFingerprint.calculate(unsigned, SIGNING_KEY));

        assertThat(OrderBookReplayArtifactFingerprint.matches(signed, SIGNING_KEY)).isTrue();
    }

    private OrderBookReplayArtifact withFingerprint(OrderBookReplayArtifact artifact, String fingerprint) {
        return new OrderBookReplayArtifact(artifact.artifactId(), artifact.semanticIdentity(), fingerprint,
                artifact.marketId(), artifact.depth(), artifact.sequenceId(), artifact.evaluationCutoff(),
                artifact.latestOccurredAt(), artifact.latestKnownAt(), artifact.sourceStartOrdinal(),
                artifact.sourceEndOrdinal(), artifact.provider(), artifact.providerSymbol(),
                artifact.normalizationVersion(), artifact.artifactSchemaVersion(), artifact.sourceEvidenceFingerprint(),
                artifact.sourceTrustStatus(), artifact.sourceEvidenceFingerprints(), artifact.bids(), artifact.asks(),
                artifact.promotionReason(), artifact.createdAt(), artifact.sourceEvidenceOrigin());
    }

    private OrderBookReplayArtifact artifact(String schema, OrderBookEvidenceOrigin origin, String fingerprint) {
        return new OrderBookReplayArtifact(UUID.randomUUID(), "research:test", fingerprint, MARKET_ID, 25,
                SEQUENCE_ID, CUTOFF, CUTOFF, CUTOFF, 0L, 0L, "KRAKEN", "PF_XBTUSD", "v1", schema,
                "evidence-fingerprint", "TRUSTED", List.of("evidence-fingerprint"),
                List.of(new OrderBookLevel(java.math.BigDecimal.ONE, java.math.BigDecimal.ONE)),
                List.of(new OrderBookLevel(java.math.BigDecimal.TWO, java.math.BigDecimal.ONE)),
                "test", CUTOFF, origin);
    }
}
