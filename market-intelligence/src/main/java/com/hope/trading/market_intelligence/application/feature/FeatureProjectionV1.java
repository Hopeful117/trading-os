package com.hope.trading.market_intelligence.application.feature;

import com.hope.trading.market_intelligence.application.capability.ProductionArtifactTypes;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.OhlcTemporalAnalysis;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.artifact.StoredArtifact;
import com.hope.trading.market_intelligence.domain.artifact.DeterministicMeasurements;
import com.hope.trading.market_intelligence.domain.capability.ProducedArtifact;
import com.hope.trading.market_intelligence.domain.feature.*;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureArtifactContent;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureAvailability;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureRelationEvidence;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureResult;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureSwingType;
import com.hope.trading.market_intelligence.domain.trendcontext.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

public final class FeatureProjectionV1 {
    public static final String VERSION = "feature-projection-v1";

    private FeatureProjectionV1() {
    }

    public static FeatureSchema schema() {
        List<FeatureDefinition> definitions = new ArrayList<>();
        int[] order = {0};
        add(definitions, order, "ohlc.range_fraction", FeatureGroup.OHLC, FeatureDataType.NUMERIC,
                FeatureUnit.DECIMAL_FRACTION, "ohlc-range-analysis", "rangePercentage / 100",
                "Bounded OHLC range percentage converted to a decimal fraction.");
        add(definitions, order, "ohlc.simple_return", FeatureGroup.OHLC, FeatureDataType.NUMERIC,
                FeatureUnit.DECIMAL_FRACTION, "ohlc-range-analysis", "simpleReturn",
                "Simple return over the replayed OHLC evidence window.");

        for (String role : roles()) {
            add(definitions, order, "structure." + role + ".high_relation", FeatureGroup.STRUCTURE,
                    FeatureDataType.CATEGORICAL, FeatureUnit.NONE, "market-structure-analysis",
                    role + ".latestHighRelation", "Latest confirmed high relation for the context role.");
            add(definitions, order, "structure." + role + ".low_relation", FeatureGroup.STRUCTURE,
                    FeatureDataType.CATEGORICAL, FeatureUnit.NONE, "market-structure-analysis",
                    role + ".latestLowRelation", "Latest confirmed low relation for the context role.");
            add(definitions, order, "structure." + role + ".confirmed_high_count", FeatureGroup.STRUCTURE,
                    FeatureDataType.NUMERIC, FeatureUnit.COUNT, "market-structure-analysis",
                    role + ".retainedHighCount", "Count of retained confirmed high swings.");
            add(definitions, order, "structure." + role + ".confirmed_low_count", FeatureGroup.STRUCTURE,
                    FeatureDataType.NUMERIC, FeatureUnit.COUNT, "market-structure-analysis",
                    role + ".retainedLowCount", "Count of retained confirmed low swings.");
            add(definitions, order, "structure." + role + ".suppressed_swing_count", FeatureGroup.STRUCTURE,
                    FeatureDataType.NUMERIC, FeatureUnit.COUNT, "market-structure-analysis",
                    role + ".suppressedSwingCount", "Count of structurally suppressed swings.");
        }

        add(definitions, order, "trend.alignment", FeatureGroup.TREND, FeatureDataType.CATEGORICAL,
                FeatureUnit.NONE, "trend-context-analysis", "alignment", "Multi-timeframe trend alignment.");
        add(definitions, order, "trend.attention", FeatureGroup.TREND, FeatureDataType.CATEGORICAL,
                FeatureUnit.NONE, "trend-context-analysis", "attention", "Trend Context attention state.");
        for (String role : roles()) {
            add(definitions, order, "trend." + role + ".direction", FeatureGroup.TREND,
                    FeatureDataType.CATEGORICAL, FeatureUnit.NONE, "trend-context-analysis",
                    role + ".direction", "Trend direction for the context role.");
            add(definitions, order, "trend." + role + ".regime", FeatureGroup.TREND,
                    FeatureDataType.CATEGORICAL, FeatureUnit.NONE, "trend-context-analysis",
                    role + ".regime", "Trend regime for the context role.");
            add(definitions, order, "trend." + role + ".phase", FeatureGroup.TREND,
                    FeatureDataType.CATEGORICAL, FeatureUnit.NONE, "trend-context-analysis",
                    role + ".phase", "Trend phase for the context role.");
            add(definitions, order, "trend." + role + ".ema_slope", FeatureGroup.TREND,
                    FeatureDataType.CATEGORICAL, FeatureUnit.NONE, "trend-context-analysis",
                    role + ".ema.slopeClassification", "EMA slope classification for the context role.");
            add(definitions, order, "trend." + role + ".atr_ratio", FeatureGroup.TREND,
                    FeatureDataType.NUMERIC, FeatureUnit.RATIO, "trend-context-analysis",
                    role + ".atr.ratio", "Current ATR relative to its baseline when available.");
            add(definitions, order, "trend." + role + ".pullback_qualified", FeatureGroup.TREND,
                    FeatureDataType.BOOLEAN, FeatureUnit.NONE, "trend-context-analysis",
                    role + ".pullback.qualified", "Whether the deterministic pullback rule qualified.");
            add(definitions, order, "trend." + role + ".extension_state", FeatureGroup.TREND,
                    FeatureDataType.CATEGORICAL, FeatureUnit.NONE, "trend-context-analysis",
                    role + ".extension", "Extension state when extension evidence is available.");
            add(definitions, order, "trend." + role + ".break_status", FeatureGroup.TREND,
                    FeatureDataType.CATEGORICAL, FeatureUnit.NONE, "trend-context-analysis",
                    role + ".structuralBreak.status", "Structural break status for the context role.");
        }
        return new FeatureSchema(VERSION, definitions);
    }

    public static ProjectionResult project(
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
        FeatureSchema schema = schema();
        DeterministicMeasurements ohlc = singleContent(ohlcArtifacts, DeterministicMeasurements.class);
        TrendContextCapabilityContent trend = singleContent(trendArtifacts, TrendContextCapabilityContent.class);
        List<ProducedArtifact> structures = structureArtifacts.stream()
                .filter(value -> value.artifact().content() instanceof MarketStructureArtifactContent)
                .sorted(Comparator.comparing(value -> ((MarketStructureArtifactContent) value.artifact().content()).result().interval()))
                .toList();

        List<FeatureValue> values = new ArrayList<>();
        values.add(numeric("ohlc.range_fraction", ohlc == null ? FeatureMissingness.UNAVAILABLE : status(ohlc.values().containsKey("rangePercentage")),
                ohlc == null ? null : value(ohlc.values(), "rangePercentage").map(value -> value.divide(BigDecimal.valueOf(100))).orElse(null)));
        values.add(numeric("ohlc.simple_return", ohlc == null ? FeatureMissingness.UNAVAILABLE : status(ohlc.values().containsKey("simpleReturn")),
                ohlc == null ? null : ohlc.values().get("simpleReturn")));

        TrendContextAssessment assessment = trend == null ? null : trend.assessment();
        for (String roleName : roles()) {
            TrendContextRole role = TrendContextRole.valueOf(roleName.toUpperCase(Locale.ROOT));
            TrendContextTimeframeAssessment timeframe = assessment == null ? null : assessment.timeframes().get(role);
            MarketStructureResult structure = matchingStructure(structures, timeframe);
            FeatureMissingness structureStatus = structure == null
                    ? FeatureMissingness.UNAVAILABLE : structureStatus(structure.availability());
            String highRelation = structure == null ? null : latestRelation(structure, MarketStructureSwingType.HIGH);
            String lowRelation = structure == null ? null : latestRelation(structure, MarketStructureSwingType.LOW);
            values.add(categorical("structure." + roleName + ".high_relation",
                    highRelation == null && structure != null ? FeatureMissingness.NOT_APPLICABLE : structureStatus,
                    highRelation));
            values.add(categorical("structure." + roleName + ".low_relation",
                    lowRelation == null && structure != null ? FeatureMissingness.NOT_APPLICABLE : structureStatus,
                    lowRelation));
            values.add(numeric("structure." + roleName + ".confirmed_high_count", structureStatus,
                    structure == null ? null : count(structure, MarketStructureSwingType.HIGH)));
            values.add(numeric("structure." + roleName + ".confirmed_low_count", structureStatus,
                    structure == null ? null : count(structure, MarketStructureSwingType.LOW)));
            values.add(numeric("structure." + roleName + ".suppressed_swing_count", structureStatus,
                    structure == null ? null : BigDecimal.valueOf(structure.suppressed().size())));
        }

        values.add(categorical("trend.alignment", assessment == null ? FeatureMissingness.UNAVAILABLE : FeatureMissingness.PRESENT,
                assessment == null ? null : assessment.alignment().name()));
        values.add(categorical("trend.attention", assessment == null ? FeatureMissingness.UNAVAILABLE : FeatureMissingness.PRESENT,
                assessment == null ? null : assessment.attention().name()));
        for (String roleName : roles()) {
            TrendContextRoleTimeframeFeatures features = TrendContextRoleTimeframeFeatures.of(
                    roleName, assessment == null ? null : assessment.timeframes().get(TrendContextRole.valueOf(roleName.toUpperCase(Locale.ROOT))));
            values.add(categorical("trend." + roleName + ".direction", features.status, features.timeframe == null ? null : features.timeframe.direction().name()));
            values.add(categorical("trend." + roleName + ".regime", features.status, features.timeframe == null ? null : features.timeframe.regime().name()));
            values.add(categorical("trend." + roleName + ".phase", features.status, features.timeframe == null ? null : features.timeframe.phase().name()));
            values.add(categorical("trend." + roleName + ".ema_slope", features.emaStatus, features.timeframe == null || !features.timeframe.ema().available() ? null : features.timeframe.ema().slopeClassification().name()));
            values.add(numeric("trend." + roleName + ".atr_ratio", features.atrStatus, features.timeframe == null || !features.timeframe.atr().available() ? null : features.timeframe.atr().ratio()));
            values.add(booleanValue("trend." + roleName + ".pullback_qualified", features.pullbackStatus, features.timeframe == null || features.timeframe.pullback().evidence() == null ? null : features.timeframe.pullback().qualified()));
            values.add(categorical("trend." + roleName + ".extension_state", features.extensionStatus,
                    features.timeframe == null || !features.timeframe.extension().available() ? null
                            : features.timeframe.extension().extended() ? "EXTENDED" : "NOT_EXTENDED"));
            values.add(categorical("trend." + roleName + ".break_status", features.status,
                    features.timeframe == null ? null : features.timeframe.structuralBreak().status().name()));
        }

        List<FeatureGroupStatus> groups = List.of(
                new FeatureGroupStatus(FeatureGroup.OHLC,
                        ohlc == null ? FeatureMissingness.UNAVAILABLE : FeatureMissingness.PRESENT,
                        ohlc == null ? List.of("OHLC artifact is unavailable") : List.of()),
                new FeatureGroupStatus(FeatureGroup.STRUCTURE,
                 structures.isEmpty() ? FeatureMissingness.UNAVAILABLE : groupStructureStatus(structures),
                        structures.isEmpty() ? List.of("Market Structure artifacts are unavailable") : List.of()),
                new FeatureGroupStatus(FeatureGroup.TREND,
                        assessment == null ? FeatureMissingness.UNAVAILABLE : FeatureMissingness.PRESENT,
                        assessment == null ? List.of("Trend Context artifact is unavailable") : trend.diagnostics())
        );
        List<AnalyzerProvenance> provenance = provenance(ohlcArtifacts, structures, trendArtifacts);
        String fingerprint = FeatureSnapshotFingerprint.fingerprint(schema, marketId, cutoff,
                evidencePolicy, contextFingerprint, values, groups, provenance);
        return new ProjectionResult(schema, values, groups, provenance, fingerprint);
    }

    private static List<AnalyzerProvenance> provenance(
            List<ProducedArtifact> ohlcArtifacts,
            List<ProducedArtifact> structures,
            List<ProducedArtifact> trendArtifacts) {
        List<AnalyzerProvenance> result = new ArrayList<>();
        ohlcArtifacts.forEach(value -> {
            StoredArtifact artifact = value.artifact();
            DeterministicMeasurements measurements = (DeterministicMeasurements) artifact.content();
            String resultFingerprint = ArtifactFingerprint.ofParameters(Map.of(
                    "title", measurements.title(),
                    "explanation", measurements.explanation(),
                    "values", measurements.values(),
                    "observedAt", measurements.observedAt())).value();
            result.add(new AnalyzerProvenance(artifact.provenance().producerId(), artifact.provenance().producerVersion(),
                     OhlcTemporalAnalysis.ALGORITHM_VERSION, null, null, null, null, null,
                    artifact.key().parametersFingerprint().value(), artifact.key().inputFingerprint().value(), resultFingerprint,
                    artifact.provenance().evidenceFingerprint(), List.of(),
                    artifact.key().identity().artifactType(), artifact.key().identity().producerId(),
                    artifact.key().identity().producerVersion(), artifact.key().parametersFingerprint().value(),
                    artifact.key().inputFingerprint().value(), value.version().value()));
        });
        structures.forEach(artifact -> {
            MarketStructureArtifactContent content = (MarketStructureArtifactContent) artifact.artifact().content();
            MarketStructureResult value = content.result();
            result.add(new AnalyzerProvenance("market-structure-analysis", value.ruleVersion(), value.algorithmId(),
                    value.ruleVersion(), value.policyId(), value.policyVersion(), value.policyId(), value.policyVersion(),
                    value.parameterFingerprint(), value.inputFingerprint(), value.resultFingerprint(),
                    artifact.artifact().provenance().evidenceFingerprint(), List.of(),
                    artifact.artifact().key().identity().artifactType(),
                    artifact.artifact().key().identity().producerId(),
                    artifact.artifact().key().identity().producerVersion(),
                    artifact.artifact().key().parametersFingerprint().value(),
                    artifact.artifact().key().inputFingerprint().value(), artifact.version().value()));
        });
        trendArtifacts.forEach(value -> {
            StoredArtifact artifact = value.artifact();
            TrendContextCapabilityContent content = (TrendContextCapabilityContent) artifact.content();
            TrendContextAssessment assessment = content.assessment();
            result.add(new AnalyzerProvenance(artifact.provenance().producerId(), artifact.provenance().producerVersion(),
                    assessment.ruleVersion(), assessment.ruleVersion(), assessment.profileId(), assessment.profileVersion(),
                    null, null, null, content.inputFingerprint(), content.assessmentFingerprint(),
                    artifact.provenance().evidenceFingerprint(), List.of(),
                    artifact.key().identity().artifactType(), artifact.key().identity().producerId(),
                    artifact.key().identity().producerVersion(), artifact.key().parametersFingerprint().value(),
                    artifact.key().inputFingerprint().value(), value.version().value()));
        });
        return result.stream().sorted(Comparator.comparing(AnalyzerProvenance::sourceCapability)
                .thenComparing(value -> Objects.toString(value.inputFingerprint(), ""))).toList();
    }

    private static FeatureMissingness groupStructureStatus(List<ProducedArtifact> values) {
        return values.stream()
                .map(value -> (MarketStructureArtifactContent) value.artifact().content())
                .allMatch(value -> value.result().availability() == MarketStructureAvailability.AVAILABLE)
                ? FeatureMissingness.PRESENT
                : structureStatus(((MarketStructureArtifactContent) values.getFirst().artifact().content()).result().availability());
    }

    private static MarketStructureResult matchingStructure(List<ProducedArtifact> structures,
                                                           TrendContextTimeframeAssessment timeframe) {
        if (timeframe == null) return null;
        return structures.stream()
                .map(value -> ((MarketStructureArtifactContent) value.artifact().content()).result())
                .filter(value -> value.interval().equals(timeframe.interval())).findFirst().orElse(null);
    }

    private static String latestRelation(MarketStructureResult result, MarketStructureSwingType type) {
        return result.latestRelation(type, result.cutOffAt()).map(MarketStructureRelationEvidence::relation)
                .map(Enum::name).orElse(null);
    }

    private static BigDecimal count(MarketStructureResult result, MarketStructureSwingType type) {
        return BigDecimal.valueOf(result.retained().stream().filter(value -> value.type() == type).count());
    }

    private static FeatureMissingness structureStatus(MarketStructureAvailability status) {
        return switch (status) {
            case AVAILABLE -> FeatureMissingness.PRESENT;
            case STALE -> FeatureMissingness.STALE_INPUT;
            case INSUFFICIENT -> FeatureMissingness.INSUFFICIENT_DATA;
            case INVALID -> FeatureMissingness.INVALID_INPUT;
            case UNAVAILABLE -> FeatureMissingness.UNAVAILABLE;
        };
    }

    private static <T> T singleContent(List<ProducedArtifact> artifacts, Class<T> type) {
        List<T> values = artifacts.stream().map(ProducedArtifact::artifact).map(StoredArtifact::content)
                .filter(type::isInstance).map(type::cast).toList();
        if (values.size() > 1) throw new IllegalStateException("Multiple artifacts for " + type.getSimpleName());
        return values.isEmpty() ? null : values.getFirst();
    }

    private static Optional<BigDecimal> value(Map<String, BigDecimal> values, String name) {
        return Optional.ofNullable(values.get(name));
    }

    private static FeatureMissingness status(boolean present) {
        return present ? FeatureMissingness.PRESENT : FeatureMissingness.INSUFFICIENT_DATA;
    }

    private static NumericFeatureValue numeric(String name, FeatureMissingness status, BigDecimal value) {
        return new NumericFeatureValue(name, value == null ? status : FeatureMissingness.PRESENT, value);
    }

    private static CategoricalFeatureValue categorical(String name, FeatureMissingness status, String value) {
        return new CategoricalFeatureValue(name, value == null ? status : FeatureMissingness.PRESENT, value);
    }

    private static BooleanFeatureValue booleanValue(String name, FeatureMissingness status, Boolean value) {
        return new BooleanFeatureValue(name, value == null ? status : FeatureMissingness.PRESENT, value);
    }

    private static void add(List<FeatureDefinition> definitions, int[] order, String name, FeatureGroup group,
                            FeatureDataType type, FeatureUnit unit, String source, String semantic, String description) {
        definitions.add(new FeatureDefinition(name, group, type, unit, source, semantic, order[0]++,
                FeatureMissingnessPolicy.NULL_WHEN_UNAVAILABLE, description));
    }

    private static List<String> roles() {
        return List.of("bias", "setup", "trigger");
    }

    public record ProjectionResult(
            FeatureSchema schema,
            List<FeatureValue> values,
            List<FeatureGroupStatus> groups,
            List<AnalyzerProvenance> provenance,
            String fingerprint
    ) {
        public ProjectionResult {
            values = List.copyOf(values);
            groups = List.copyOf(groups);
            provenance = List.copyOf(provenance);
        }
    }

    private record TrendContextRoleTimeframeFeatures(
            TrendContextTimeframeAssessment timeframe,
            FeatureMissingness status,
            FeatureMissingness emaStatus,
            FeatureMissingness atrStatus,
            FeatureMissingness pullbackStatus,
            FeatureMissingness extensionStatus
    ) {
        private static TrendContextRoleTimeframeFeatures of(String role, TrendContextTimeframeAssessment timeframe) {
            FeatureMissingness status = timeframe == null ? FeatureMissingness.UNAVAILABLE : FeatureMissingness.PRESENT;
            return new TrendContextRoleTimeframeFeatures(timeframe, status,
                    timeframe == null ? FeatureMissingness.UNAVAILABLE
                            : timeframe.ema().available() ? FeatureMissingness.PRESENT : FeatureMissingness.INSUFFICIENT_DATA,
                    timeframe == null ? FeatureMissingness.UNAVAILABLE
                            : timeframe.atr().available() ? FeatureMissingness.PRESENT : FeatureMissingness.INSUFFICIENT_DATA,
                    timeframe == null ? FeatureMissingness.UNAVAILABLE
                            : timeframe.pullback().evidence() == null ? FeatureMissingness.UNAVAILABLE : FeatureMissingness.PRESENT,
                    timeframe == null ? FeatureMissingness.UNAVAILABLE
                            : timeframe.extension().available() ? FeatureMissingness.PRESENT : FeatureMissingness.UNAVAILABLE);
        }
    }
}
