package com.hope.trading.market_intelligence.application.feature;

import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.feature.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

final class FeatureSnapshotFingerprint {
    private FeatureSnapshotFingerprint() {
    }

    static String fingerprint(
            FeatureSchema schema,
            UUID marketId,
            Instant cutoff,
            EvidenceAcquisitionPolicy policy,
            String contextFingerprint,
            List<FeatureValue> values,
            List<FeatureGroupStatus> groups,
            List<AnalyzerProvenance> provenance
    ) {
        List<String> material = new ArrayList<>();
        material.add("projection=" + schema.version());
        material.add("market=" + marketId);
        material.add("cutoff=" + cutoff);
        material.add("policy=" + policy);
        material.add("context=" + contextFingerprint);
        schema.definitions().forEach(definition -> material.add("definition[" + definition.order() + "]="
                + definition.name() + "|" + definition.group() + "|" + definition.dataType() + "|"
                + definition.unit() + "|" + definition.sourceCapability() + "|" + definition.sourceSemantic()
                + "|" + definition.missingnessPolicy() + "|" + definition.description()));
        for (int index = 0; index < values.size(); index++) {
            material.add("value[" + String.format("%03d", index) + "]=" + canonical(values.get(index)));
        }
        groups.stream().sorted(Comparator.comparing(value -> value.group().name()))
                .forEach(value -> material.add("group=" + value.group() + "|" + value.status() + "|" + value.diagnostics()));
        provenance.forEach(value -> material.add("provenance=" + value));
        return ArtifactFingerprint.ofInputs(material).value();
    }

    private static String canonical(FeatureValue value) {
        String content = switch (value) {
            case NumericFeatureValue numeric -> canonical(numeric.value());
            case CategoricalFeatureValue categorical -> Objects.toString(categorical.value(), "null");
            case BooleanFeatureValue booleanValue -> Objects.toString(booleanValue.value(), "null");
        };
        return value.name() + "|" + value.getClass().getSimpleName() + "|" + value.status() + "|" + content;
    }

    private static String canonical(BigDecimal value) {
        return value == null ? "null" : value.stripTrailingZeros().toPlainString();
    }
}
