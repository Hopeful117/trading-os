package com.hope.trading.market_intelligence.application.feature;

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

public final class FeatureProjectionV1 {
    public static final String VERSION = "feature-projection-v1";
    private static final String SIMPLE_RETURN = "simpleReturn";
    private static final String STRUCTURE = "structure";
    private static final String MARKET_STRUCTURE_ANALYSIS = "market-structure-analysis";
    private static final String TREND_CONTEXT_ANALYSIS = "trend-context-analysis";
    private static final String TREND = "trend";
    private static final String DIRECTION = ".direction";
    private static final String REGIME = ".regime";
    private static final String PHASE = ".phase";

    private FeatureProjectionV1() {
    }

    public static FeatureSchema schema() {
        List<FeatureDefinition> definitions = new ArrayList<>();
        int[] order = {0};
        add(definitions, order, new DefinitionSpec("ohlc.range_fraction", FeatureGroup.OHLC, FeatureDataType.NUMERIC,
                FeatureUnit.DECIMAL_FRACTION, "ohlc-range-analysis", "rangePercentage / 100",
                "Bounded OHLC range percentage converted to a decimal fraction."));
        add(definitions, order, new DefinitionSpec("ohlc.simple_return", FeatureGroup.OHLC, FeatureDataType.NUMERIC,
                FeatureUnit.DECIMAL_FRACTION, "ohlc-range-analysis", SIMPLE_RETURN,
                "Simple return over the replayed OHLC evidence window."));

        for (String role : roles()) {
            add(definitions, order, new DefinitionSpec(STRUCTURE + "." + role + ".high_relation", FeatureGroup.STRUCTURE,
                    FeatureDataType.CATEGORICAL, FeatureUnit.NONE, MARKET_STRUCTURE_ANALYSIS,
                    role + ".latestHighRelation", "Latest confirmed high relation for the context role."));
            add(definitions, order, new DefinitionSpec(STRUCTURE + "." + role + ".low_relation", FeatureGroup.STRUCTURE,
                    FeatureDataType.CATEGORICAL, FeatureUnit.NONE, MARKET_STRUCTURE_ANALYSIS,
                    role + ".latestLowRelation", "Latest confirmed low relation for the context role."));
            add(definitions, order, new DefinitionSpec(STRUCTURE + "." + role + ".confirmed_high_count", FeatureGroup.STRUCTURE,
                    FeatureDataType.NUMERIC, FeatureUnit.COUNT, MARKET_STRUCTURE_ANALYSIS,
                    role + ".retainedHighCount", "Count of retained confirmed high swings."));
            add(definitions, order, new DefinitionSpec(STRUCTURE + "." + role + ".confirmed_low_count", FeatureGroup.STRUCTURE,
                    FeatureDataType.NUMERIC, FeatureUnit.COUNT, MARKET_STRUCTURE_ANALYSIS,
                    role + ".retainedLowCount", "Count of retained confirmed low swings."));
            add(definitions, order, new DefinitionSpec(STRUCTURE + "." + role + ".suppressed_swing_count", FeatureGroup.STRUCTURE,
                    FeatureDataType.NUMERIC, FeatureUnit.COUNT, MARKET_STRUCTURE_ANALYSIS,
                    role + ".suppressedSwingCount", "Count of structurally suppressed swings."));
        }

        add(definitions, order, new DefinitionSpec("trend.alignment", FeatureGroup.TREND, FeatureDataType.CATEGORICAL,
                FeatureUnit.NONE, TREND_CONTEXT_ANALYSIS, "alignment", "Multi-timeframe trend alignment."));
        add(definitions, order, new DefinitionSpec("trend.attention", FeatureGroup.TREND, FeatureDataType.CATEGORICAL,
                FeatureUnit.NONE, TREND_CONTEXT_ANALYSIS, "attention", "Trend Context attention state."));
        for (String role : roles()) {
            add(definitions, order, new DefinitionSpec(TREND + "." + role + DIRECTION, FeatureGroup.TREND,
                    FeatureDataType.CATEGORICAL, FeatureUnit.NONE, TREND_CONTEXT_ANALYSIS,
                    role + DIRECTION, "Trend direction for the context role."));
            add(definitions, order, new DefinitionSpec(TREND + "." + role + REGIME, FeatureGroup.TREND,
                    FeatureDataType.CATEGORICAL, FeatureUnit.NONE, TREND_CONTEXT_ANALYSIS,
                    role + REGIME, "Trend regime for the context role."));
            add(definitions, order, new DefinitionSpec(TREND + "." + role + PHASE, FeatureGroup.TREND,
                    FeatureDataType.CATEGORICAL, FeatureUnit.NONE, TREND_CONTEXT_ANALYSIS,
                    role + PHASE, "Trend phase for the context role."));
            add(definitions, order, new DefinitionSpec(TREND + "." + role + ".ema_slope", FeatureGroup.TREND,
                    FeatureDataType.CATEGORICAL, FeatureUnit.NONE, TREND_CONTEXT_ANALYSIS,
                    role + ".ema.slopeClassification", "EMA slope classification for the context role."));
            add(definitions, order, new DefinitionSpec(TREND + "." + role + ".atr_ratio", FeatureGroup.TREND,
                    FeatureDataType.NUMERIC, FeatureUnit.RATIO, TREND_CONTEXT_ANALYSIS,
                    role + ".atr.ratio", "Current ATR relative to its baseline when available."));
            add(definitions, order, new DefinitionSpec(TREND + "." + role + ".pullback_qualified", FeatureGroup.TREND,
                    FeatureDataType.BOOLEAN, FeatureUnit.NONE, TREND_CONTEXT_ANALYSIS,
                    role + ".pullback.qualified", "Whether the deterministic pullback rule qualified."));
            add(definitions, order, new DefinitionSpec(TREND + "." + role + ".extension_state", FeatureGroup.TREND,
                    FeatureDataType.CATEGORICAL, FeatureUnit.NONE, TREND_CONTEXT_ANALYSIS,
                    role + ".extension", "Extension state when extension evidence is available."));
            add(definitions, order, new DefinitionSpec(TREND + "." + role + ".break_status", FeatureGroup.TREND,
                    FeatureDataType.CATEGORICAL, FeatureUnit.NONE, TREND_CONTEXT_ANALYSIS,
                    role + ".structuralBreak.status", "Structural break status for the context role."));
        }
        return new FeatureSchema(VERSION, definitions);
    }

    /**
     * Signature retained for serialized/replay callers; the execution id and creation time are
     * supplied by that contract but are intentionally not fingerprint material.
     */
    @SuppressWarnings({"java:S107", "java:S1172"})
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

        TrendContextAssessment assessment = trend == null ? null : trend.assessment();
        List<FeatureValue> values = new ArrayList<>();
        values.addAll(ohlcValues(ohlc));
        values.addAll(structureValues(structures, assessment));
        values.addAll(trendValues(assessment));

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
        String fingerprint = FeatureSnapshotFingerprint.fingerprint(new FeatureSnapshotFingerprint.FingerprintInput(
                schema, marketId, cutoff, evidencePolicy, contextFingerprint, values, groups, provenance));
        return new ProjectionResult(schema, values, groups, provenance, fingerprint);
    }

    private static List<FeatureValue> ohlcValues(DeterministicMeasurements ohlc) {
        return List.of(
                numeric("ohlc.range_fraction", ohlc == null ? FeatureMissingness.UNAVAILABLE : status(ohlc.values().containsKey("rangePercentage")),
                        ohlc == null ? null : value(ohlc.values(), "rangePercentage")
                                .map(item -> item.divide(BigDecimal.valueOf(100))).orElse(null)),
                numeric("ohlc.simple_return", ohlc == null ? FeatureMissingness.UNAVAILABLE : status(ohlc.values().containsKey(SIMPLE_RETURN)),
                        ohlc == null ? null : ohlc.values().get(SIMPLE_RETURN)));
    }

    private static List<FeatureValue> structureValues(
             List<ProducedArtifact> structures, TrendContextAssessment assessment) {
        List<FeatureValue> values = new ArrayList<>();
        for (String roleName : roles()) {
            values.addAll(structureValuesForRole(structures, assessment, roleName));
        }
        return values;
    }

    private static List<FeatureValue> structureValuesForRole(
            List<ProducedArtifact> structures, TrendContextAssessment assessment, String roleName) {
        TrendContextRole role = TrendContextRole.valueOf(roleName.toUpperCase(Locale.ROOT));
        TrendContextTimeframeAssessment timeframe = assessment == null ? null : assessment.timeframes().get(role);
        MarketStructureResult structure = matchingStructure(structures, timeframe);
        FeatureMissingness status = structure == null
                ? FeatureMissingness.UNAVAILABLE : structureStatus(structure.availability());
        String highRelation = structure == null ? null : latestRelation(structure, MarketStructureSwingType.HIGH);
        String lowRelation = structure == null ? null : latestRelation(structure, MarketStructureSwingType.LOW);
        return List.of(
                categorical(STRUCTURE + "." + roleName + ".high_relation", relationStatus(status, structure, highRelation), highRelation),
                categorical(STRUCTURE + "." + roleName + ".low_relation", relationStatus(status, structure, lowRelation), lowRelation),
                numeric(STRUCTURE + "." + roleName + ".confirmed_high_count", status,
                        structure == null ? null : count(structure, MarketStructureSwingType.HIGH)),
                numeric(STRUCTURE + "." + roleName + ".confirmed_low_count", status,
                        structure == null ? null : count(structure, MarketStructureSwingType.LOW)),
                numeric(STRUCTURE + "." + roleName + ".suppressed_swing_count", status,
                        structure == null ? null : BigDecimal.valueOf(structure.suppressed().size())));
    }

    private static FeatureMissingness relationStatus(
            FeatureMissingness status, MarketStructureResult structure, String relation) {
        return relation == null && structure != null ? FeatureMissingness.NOT_APPLICABLE : status;
    }

    private static List<FeatureValue> trendValues(TrendContextAssessment assessment) {
        List<FeatureValue> values = new ArrayList<>();
        FeatureMissingness status = assessment == null ? FeatureMissingness.UNAVAILABLE : FeatureMissingness.PRESENT;
        values.add(categorical("trend.alignment", status, assessment == null ? null : assessment.alignment().name()));
        values.add(categorical("trend.attention", status, assessment == null ? null : assessment.attention().name()));
        for (String roleName : roles()) {
            values.addAll(trendValuesForRole(assessment, roleName));
        }
        return values;
    }

    private static List<FeatureValue> trendValuesForRole(TrendContextAssessment assessment, String roleName) {
        TrendContextTimeframeAssessment timeframe = assessment == null ? null
                : assessment.timeframes().get(TrendContextRole.valueOf(roleName.toUpperCase(Locale.ROOT)));
        TrendContextRoleTimeframeFeatures features = TrendContextRoleTimeframeFeatures.of(timeframe);
        return List.of(
                categorical(TREND + "." + roleName + DIRECTION, features.status,
                        features.timeframe == null ? null : features.timeframe.direction().name()),
                categorical(TREND + "." + roleName + REGIME, features.status,
                        features.timeframe == null ? null : features.timeframe.regime().name()),
                categorical(TREND + "." + roleName + PHASE, features.status,
                        features.timeframe == null ? null : features.timeframe.phase().name()),
                categorical(TREND + "." + roleName + ".ema_slope", features.emaStatus,
                        features.timeframe == null || !features.timeframe.ema().available()
                                ? null : features.timeframe.ema().slopeClassification().name()),
                numeric(TREND + "." + roleName + ".atr_ratio", features.atrStatus,
                        features.timeframe == null || !features.timeframe.atr().available()
                                ? null : features.timeframe.atr().ratio()),
                booleanValue(TREND + "." + roleName + ".pullback_qualified", features.pullbackStatus,
                        features.timeframe == null || features.timeframe.pullback().evidence() == null
                                ? null : features.timeframe.pullback().qualified()),
                categorical(TREND + "." + roleName + ".extension_state", features.extensionStatus,
                        extensionState(features.timeframe)),
                categorical(TREND + "." + roleName + ".break_status", features.status,
                        features.timeframe == null ? null : features.timeframe.structuralBreak().status().name()));
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
            result.add(new AnalyzerProvenance(MARKET_STRUCTURE_ANALYSIS, value.ruleVersion(), value.algorithmId(),
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

    private static String extensionState(TrendContextTimeframeAssessment timeframe) {
        if (timeframe == null || !timeframe.extension().available()) return null;
        return timeframe.extension().extended() ? "EXTENDED" : "NOT_EXTENDED";
    }

    private static void add(List<FeatureDefinition> definitions, int[] order, DefinitionSpec specification) {
        definitions.add(new FeatureDefinition(specification.name(), specification.group(), specification.type(),
                specification.unit(), specification.source(), specification.semantic(), order[0]++,
                FeatureMissingnessPolicy.NULL_WHEN_UNAVAILABLE, specification.description()));
    }

    private record DefinitionSpec(
            String name,
            FeatureGroup group,
            FeatureDataType type,
            FeatureUnit unit,
            String source,
            String semantic,
            String description
    ) { }

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
        private static TrendContextRoleTimeframeFeatures of(TrendContextTimeframeAssessment timeframe) {
            FeatureMissingness status = timeframe == null ? FeatureMissingness.UNAVAILABLE : FeatureMissingness.PRESENT;
            FeatureMissingness emaStatus = timeframe == null ? FeatureMissingness.UNAVAILABLE : emaStatus(timeframe);
            FeatureMissingness atrStatus = timeframe == null ? FeatureMissingness.UNAVAILABLE : atrStatus(timeframe);
            FeatureMissingness pullbackStatus = timeframe == null ? FeatureMissingness.UNAVAILABLE : pullbackStatus(timeframe);
            FeatureMissingness extensionStatus = timeframe == null ? FeatureMissingness.UNAVAILABLE : extensionStatus(timeframe);
            return new TrendContextRoleTimeframeFeatures(timeframe, status,
                    emaStatus, atrStatus, pullbackStatus, extensionStatus);
        }

        private static FeatureMissingness emaStatus(TrendContextTimeframeAssessment timeframe) {
            return timeframe.ema().available() ? FeatureMissingness.PRESENT : FeatureMissingness.INSUFFICIENT_DATA;
        }

        private static FeatureMissingness atrStatus(TrendContextTimeframeAssessment timeframe) {
            return timeframe.atr().available() ? FeatureMissingness.PRESENT : FeatureMissingness.INSUFFICIENT_DATA;
        }

        private static FeatureMissingness pullbackStatus(TrendContextTimeframeAssessment timeframe) {
            return timeframe.pullback().evidence() == null ? FeatureMissingness.UNAVAILABLE : FeatureMissingness.PRESENT;
        }

        private static FeatureMissingness extensionStatus(TrendContextTimeframeAssessment timeframe) {
            return timeframe.extension().available() ? FeatureMissingness.PRESENT : FeatureMissingness.UNAVAILABLE;
        }
    }
}
