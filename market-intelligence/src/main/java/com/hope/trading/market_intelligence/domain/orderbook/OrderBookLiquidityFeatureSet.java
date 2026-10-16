package com.hope.trading.market_intelligence.domain.orderbook;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactContent;
import com.hope.trading.market_intelligence.domain.feature.FeatureGroupStatus;
import com.hope.trading.market_intelligence.domain.feature.FeatureSchema;
import com.hope.trading.market_intelligence.domain.feature.FeatureValue;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrderBookLiquidityFeatureSet(
        String version,
        UUID marketId,
        int depth,
        UUID sequenceId,
        Instant evaluationCutoff,
        Instant latestOccurredAt,
        Instant latestKnownAt,
        Long sourceStartOrdinal,
        Long sourceEndOrdinal,
        String normalizationVersion,
        String sourceEvidenceFingerprint,
        FeatureSchema schema,
        List<FeatureValue> values,
        List<FeatureGroupStatus> groups,
        String algorithmVersion,
        String parameterFingerprint,
        String resultFingerprint
) implements ArtifactContent {
    public OrderBookLiquidityFeatureSet {
        required(version, "version");
        Objects.requireNonNull(marketId, "marketId");
        Objects.requireNonNull(sequenceId, "sequenceId");
        Objects.requireNonNull(evaluationCutoff, "evaluationCutoff");
        Objects.requireNonNull(latestOccurredAt, "latestOccurredAt");
        Objects.requireNonNull(latestKnownAt, "latestKnownAt");
        required(normalizationVersion, "normalizationVersion");
        required(sourceEvidenceFingerprint, "sourceEvidenceFingerprint");
        Objects.requireNonNull(schema, "schema");
        values = List.copyOf(values);
        groups = List.copyOf(groups);
        required(algorithmVersion, "algorithmVersion");
        required(parameterFingerprint, "parameterFingerprint");
        required(resultFingerprint, "resultFingerprint");
        if (depth < 1) throw new IllegalArgumentException("depth must be positive");
    }

    private static void required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
