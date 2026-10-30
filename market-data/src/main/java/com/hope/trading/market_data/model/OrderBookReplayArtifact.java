package com.hope.trading.market_data.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrderBookReplayArtifact(
        UUID artifactId,
        String semanticIdentity,
        String artifactFingerprint,
        UUID marketId,
        int depth,
        UUID sequenceId,
        Instant evaluationCutoff,
        Instant latestOccurredAt,
        Instant latestKnownAt,
        Long sourceStartOrdinal,
        Long sourceEndOrdinal,
        String provider,
        String providerSymbol,
        String normalizationVersion,
        String artifactSchemaVersion,
        String sourceEvidenceFingerprint,
        String sourceTrustStatus,
        List<String> sourceEvidenceFingerprints,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks,
        String promotionReason,
        Instant createdAt,
        OrderBookEvidenceOrigin sourceEvidenceOrigin
) {
    public OrderBookReplayArtifact(UUID artifactId, String semanticIdentity, String artifactFingerprint,
                                   UUID marketId, int depth, UUID sequenceId, Instant evaluationCutoff,
                                   Instant latestOccurredAt, Instant latestKnownAt, Long sourceStartOrdinal,
                                   Long sourceEndOrdinal, String provider, String providerSymbol,
                                   String normalizationVersion, String artifactSchemaVersion,
                                   String sourceEvidenceFingerprint, String sourceTrustStatus,
                                   List<String> sourceEvidenceFingerprints, List<OrderBookLevel> bids,
                                   List<OrderBookLevel> asks, String promotionReason, Instant createdAt) {
        this(artifactId, semanticIdentity, artifactFingerprint, marketId, depth, sequenceId, evaluationCutoff,
                latestOccurredAt, latestKnownAt, sourceStartOrdinal, sourceEndOrdinal, provider, providerSymbol,
                normalizationVersion, artifactSchemaVersion, sourceEvidenceFingerprint, sourceTrustStatus,
                sourceEvidenceFingerprints, bids, asks, promotionReason, createdAt,
                OrderBookEvidenceOrigin.PROVIDER);
    }

    public OrderBookReplayArtifact {
        Objects.requireNonNull(artifactId, "artifactId is required");
        Objects.requireNonNull(semanticIdentity, "semanticIdentity is required");
        Objects.requireNonNull(artifactFingerprint, "artifactFingerprint is required");
        Objects.requireNonNull(marketId, "marketId is required");
        Objects.requireNonNull(sequenceId, "sequenceId is required");
        Objects.requireNonNull(evaluationCutoff, "evaluationCutoff is required");
        Objects.requireNonNull(normalizationVersion, "normalizationVersion is required");
        Objects.requireNonNull(artifactSchemaVersion, "artifactSchemaVersion is required");
        Objects.requireNonNull(sourceEvidenceFingerprint, "sourceEvidenceFingerprint is required");
        Objects.requireNonNull(sourceTrustStatus, "sourceTrustStatus is required");
        Objects.requireNonNull(sourceEvidenceFingerprints, "source evidence fingerprints are required");
        Objects.requireNonNull(bids, "bids are required");
        Objects.requireNonNull(asks, "asks are required");
        Objects.requireNonNull(promotionReason, "promotionReason is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(sourceEvidenceOrigin, "source evidence origin is required");
        if (depth < 1) {
            throw new IllegalArgumentException("depth must be positive");
        }
        if (!"TRUSTED".equals(sourceTrustStatus) && !"UNVERIFIED".equals(sourceTrustStatus)) {
            throw new IllegalArgumentException("unsupported replay trust status");
        }
        if (sourceEvidenceFingerprints.isEmpty()
                || sourceEvidenceFingerprints.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("source evidence fingerprints are required");
        }
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
        sourceEvidenceFingerprints = List.copyOf(sourceEvidenceFingerprints);
    }
}
