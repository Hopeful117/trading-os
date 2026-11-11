package com.hope.trading.market_intelligence.application.feature;

import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.artifact.DeterministicMeasurements;
import com.hope.trading.market_intelligence.domain.capability.ProducedArtifact;
import com.hope.trading.market_intelligence.domain.feature.FeatureDataType;
import com.hope.trading.market_intelligence.domain.feature.FeatureDefinition;
import com.hope.trading.market_intelligence.domain.feature.FeatureGroup;
import com.hope.trading.market_intelligence.domain.feature.FeatureMissingness;
import com.hope.trading.market_intelligence.domain.feature.FeatureSchema;
import com.hope.trading.market_intelligence.domain.feature.FeatureUnit;
import com.hope.trading.market_intelligence.domain.feature.FeatureValue;
import com.hope.trading.market_intelligence.domain.feature.NumericFeatureValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FeatureProjectionV2 {
    public static final String VERSION = "feature-projection-v2";

    private static final List<DefinitionSpec> VOLATILITY_DEFINITIONS = List.of(
            new DefinitionSpec("ohlc.atr", FeatureUnit.PRICE, "atr", "ATR(14) over completed OHLC candles."),
            new DefinitionSpec("ohlc.normalized_atr", FeatureUnit.DECIMAL_FRACTION, "normalizedAtr",
                    "ATR(14) normalized by the latest close."),
            new DefinitionSpec("ohlc.realized_volatility", FeatureUnit.DECIMAL_FRACTION, "realizedVolatility",
                    "Realized volatility over the latest volatility lookback."),
            new DefinitionSpec("ohlc.volatility_compression_ratio", FeatureUnit.RATIO,
                    "volatilityCompressionRatio", "Current ATR relative to its ATR baseline."),
            new DefinitionSpec("ohlc.directional_efficiency", FeatureUnit.DECIMAL_FRACTION,
                    "directionalEfficiency", "Absolute net price movement divided by total absolute movement."));

    private FeatureProjectionV2() {
    }

    public static FeatureSchema schema() {
        List<FeatureDefinition> definitions = new ArrayList<>();
        FeatureProjectionV1.schema().definitions().forEach(definition -> definitions.add(new FeatureDefinition(
                definition.name(), definition.group(), definition.dataType(), definition.unit(),
                definition.sourceCapability(), definition.sourceSemantic(), definitions.size(),
                definition.missingnessPolicy(), definition.description())));
        for (DefinitionSpec specification : VOLATILITY_DEFINITIONS) {
            definitions.add(new FeatureDefinition(specification.name(), FeatureGroup.OHLC, FeatureDataType.NUMERIC,
                    specification.unit(), "ohlc-range-analysis", specification.semantic(), definitions.size(),
                    com.hope.trading.market_intelligence.domain.feature.FeatureMissingnessPolicy.NULL_WHEN_UNAVAILABLE,
                    specification.description()));
        }
        return new FeatureSchema(VERSION, definitions);
    }

    public static FeatureProjectionV1.ProjectionResult project(
            UUID analysisExecutionId,
            UUID marketId,
            Instant cutoff,
            EvidenceAcquisitionPolicy evidencePolicy,
            String contextFingerprint,
            List<ProducedArtifact> ohlcArtifacts,
            List<ProducedArtifact> structureArtifacts,
            List<ProducedArtifact> trendArtifacts,
            Instant createdAt
    ) {
        FeatureProjectionV1.ProjectionResult base = FeatureProjectionV1.project(
                analysisExecutionId, marketId, cutoff, evidencePolicy, contextFingerprint,
                ohlcArtifacts, structureArtifacts, trendArtifacts, createdAt);
        DeterministicMeasurements ohlc = ohlcArtifacts.stream()
                .map(value -> value.artifact().content())
                .filter(DeterministicMeasurements.class::isInstance)
                .map(DeterministicMeasurements.class::cast)
                .findFirst().orElse(null);
        List<FeatureValue> values = new ArrayList<>(base.values());
        for (DefinitionSpec specification : VOLATILITY_DEFINITIONS) {
            BigDecimal value = ohlc == null ? null : ohlc.values().get(specification.semantic());
            FeatureMissingness status = ohlc == null
                    ? FeatureMissingness.UNAVAILABLE
                    : value == null ? FeatureMissingness.INSUFFICIENT_DATA : FeatureMissingness.PRESENT;
            values.add(new NumericFeatureValue(specification.name(), status, value));
        }
        FeatureSchema schema = schema();
        String fingerprint = FeatureSnapshotFingerprint.fingerprint(new FeatureSnapshotFingerprint.FingerprintInput(
                schema, marketId, cutoff, evidencePolicy, contextFingerprint, values,
                base.groups(), base.provenance()));
        return new FeatureProjectionV1.ProjectionResult(
                schema, values, base.groups(), base.provenance(), fingerprint);
    }

    private record DefinitionSpec(String name, FeatureUnit unit, String semantic, String description) {
    }
}
