package com.hope.trading.market_intelligence.application.thesis;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.observation.ObservationEvidence;
import com.hope.trading.market_intelligence.domain.observation.TrendContextObservationPayload;
import com.hope.trading.market_intelligence.domain.observation.MarketThesisObservationPayload;
import com.hope.trading.market_intelligence.domain.momentum.MomentumAssessment;
import com.hope.trading.market_intelligence.domain.momentum.MomentumCapabilityContent;
import com.hope.trading.market_intelligence.domain.thesis.*;
import com.hope.trading.market_intelligence.domain.trendcontext.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MarketThesisComposer {
    public static final String VERSION = "market-thesis-composer/v1";
    private final TrendContextProfile trendContextProfile;
    private final StructuralLevelObjectiveComposer structuralLevels =
            new StructuralLevelObjectiveComposer();

    public MarketThesisComposer(Clock clock) {
        this(clock, defaultProfile());
    }

    public MarketThesisComposer(Clock clock, TrendContextProfile trendContextProfile) {
        Objects.requireNonNull(clock, "clock");
        this.trendContextProfile = Objects.requireNonNull(trendContextProfile, "trendContextProfile");
    }

    private static TrendContextProfile defaultProfile() {
        return TrendContextProfile.conservativeSwingV1(java.util.Map.of(
                TrendContextRole.BIAS, new TrendContextRoleDefinition(
                        TrendContextRole.BIAS, "BIAS", java.time.Duration.ofHours(4), true, 1, 1),
                TrendContextRole.SETUP, new TrendContextRoleDefinition(
                        TrendContextRole.SETUP, "SETUP", java.time.Duration.ofHours(1), true, 1, 1),
                TrendContextRole.TRIGGER, new TrendContextRoleDefinition(
                        TrendContextRole.TRIGGER, "TRIGGER", java.time.Duration.ofMinutes(15), false, 1, 1)));
    }

    public MarketThesis compose(
            UUID marketId, UUID analysisExecutionId, Observation observation) {
        return compose(marketId, analysisExecutionId, observation, null);
    }

    public MarketThesis compose(
            UUID marketId, UUID analysisExecutionId, Observation observation,
            Instant authoritativeCutoff) {
        Objects.requireNonNull(marketId, "marketId");
        Objects.requireNonNull(observation, "observation");

        TrendContextAssessment assessment = trendAssessment(observation);
        MomentumCapabilityContent momentum = momentumContent(observation);
        Instant evaluatedAt = observation.createdAt() == null
                ? observation.validFrom() : observation.createdAt();
        Instant sourceCutoff = authoritativeCutoff == null ? evaluatedAt : authoritativeCutoff;
        boolean assessmentCompatible = assessment == null
                || (!assessment.cutOffAt().isAfter(sourceCutoff)
                && marketId.equals(assessment.marketId())
                && observation.instrument().equals(assessment.symbol()));
        List<MarketThesisEvidence> evidence = new ArrayList<>();

        observation.evidence().forEach(value -> evidence.add(observationEvidence(
                marketId, observation, value, sourceCutoff)));
        if (assessment == null) {
            addMissing(evidence, "TREND_CONTEXT", "TREND_CONTEXT_UNAVAILABLE",
                    MarketThesisAvailability.UNAVAILABLE, sourceCutoff,
                    "Trend Context assessment is unavailable");
        } else if (!assessmentCompatible) {
            addMissing(evidence, "TREND_CONTEXT", "TREND_CONTEXT_CUTOFF_INCOMPATIBLE",
                    MarketThesisAvailability.UNKNOWN, sourceCutoff,
                    "Trend Context assessment is newer than the requested source cutoff");
        } else {
            addTrendEvidence(evidence, assessment, sourceCutoff);
        }
        ReferencePrice referencePrice = referencePrice(observation, sourceCutoff);
        StructuralLevelObjectiveComposer.Composition structural = structuralLevels.compose(
                assessmentCompatible ? assessment : null,
                referencePrice.price(), referencePrice.observedAt(), sourceCutoff);
        evidence.addAll(structural.missingEvidence());
        addMomentumEvidence(evidence, momentum, assessment, sourceCutoff);
        addMissing(evidence, "VOLUME", "VOLUME_NOT_IMPLEMENTED",
                MarketThesisAvailability.NOT_IMPLEMENTED, sourceCutoff,
                "Volume semantics are not implemented");
        addMissing(evidence, "LIQUIDITY", "LIQUIDITY_NOT_IMPLEMENTED",
                MarketThesisAvailability.NOT_IMPLEMENTED, sourceCutoff,
                "Advanced liquidity analysis is not implemented");

        List<String> reasons = new ArrayList<>();
        reasons.addAll(structural.reasons());
        MarketThesisScenario scenario = scenario(
                assessmentCompatible ? assessment : null, momentum, reasons, structural.levels());
        MarketThesisState state = state(scenario, evidence, reasons);
        String fingerprint = fingerprint(marketId, analysisExecutionId, observation,
                assessmentCompatible ? assessment : null, momentum, scenario, reasons,
                evaluatedAt, sourceCutoff, evidence);
        UUID lineageId = deterministicId("lineage", marketId, observation.lineageId(),
                observation.id());
        UUID thesisId = deterministicId("thesis", lineageId, observation.version(), fingerprint);
        return new MarketThesis(
                thesisId, lineageId, observation.version(), marketId, observation.instrument(),
                state, scenario, evidence, reasons, evaluatedAt, sourceCutoff,
                observation.validFrom(), observation.validUntil().orElse(null),
                analysisExecutionId, VERSION, fingerprint, null);
    }

    public MarketThesis composeDegraded(
            UUID marketId, String instrument, UUID analysisExecutionId,
            String reason, Instant evaluatedAt) {
        String normalizedInstrument = instrument == null || instrument.isBlank()
                ? marketId.toString() : instrument;
        MarketThesisEvidence missing = new MarketThesisEvidence(
                deterministicId("degraded-evidence", marketId, normalizedInstrument, reason),
                MarketThesisEvidenceRole.MISSING, MarketThesisAvailability.UNAVAILABLE, null,
                "MARKET_INTELLIGENCE", "DEGRADED_COMPOSITION", null, reason,
                evaluatedAt, null, null, normalizedInstrument, null, VERSION, evaluatedAt);
        String fingerprint = ArtifactFingerprint.ofInputs(List.of(
                VERSION, marketId.toString(), normalizedInstrument, reason,
                evaluatedAt.toString())).value();
        UUID lineageId = deterministicId("degraded-lineage", marketId, normalizedInstrument,
                fingerprint);
        return new MarketThesis(
                deterministicId("degraded-thesis", lineageId, fingerprint), lineageId, 1,
                marketId, normalizedInstrument, MarketThesisState.NO_TRADE, null,
                List.of(missing), List.of("DEGRADED_COMPOSITION"), evaluatedAt, evaluatedAt,
                evaluatedAt, null, analysisExecutionId, VERSION, fingerprint, null);
    }

    private static MarketThesisEvidence observationEvidence(
            UUID marketId, Observation observation, ObservationEvidence value, Instant cutoff) {
        MarketThesisAvailability availability = value.observedAt().isAfter(cutoff)
                ? MarketThesisAvailability.UNKNOWN : observation.validUntil()
                .filter(validUntil -> validUntil.isBefore(cutoff))
                .map(unused -> MarketThesisAvailability.STALE)
                .orElse(MarketThesisAvailability.AVAILABLE);
        return new MarketThesisEvidence(
                value.evidenceId(), availability == MarketThesisAvailability.AVAILABLE
                        ? MarketThesisEvidenceRole.SUPPORTING : MarketThesisEvidenceRole.MISSING,
                availability, null,
                value.origin(), value.capabilityResult().capabilityExecutionId().toString(),
                observation.horizon(), value.explanation(), value.observedAt(),
                observation.validUntil().orElse(null), null, observation.instrument(),
                value.evidenceId().toString(), value.capabilityResult().capabilityVersion(), cutoff,
                true);
    }

    private void addTrendEvidence(
            List<MarketThesisEvidence> target, TrendContextAssessment assessment, Instant cutoff) {
        assessment.findings().forEach(value -> target.add(new MarketThesisEvidence(
                deterministicId("finding", assessment.fingerprint(), value.code(), value.ruleId()),
                MarketThesisEvidenceRole.SUPPORTING, MarketThesisAvailability.AVAILABLE, null,
                "TREND_CONTEXT", value.code(), value.role().name(), value.message(),
                assessment.assessmentAt(), null, assessment.provider(), assessment.symbol(),
                assessment.fingerprint(), assessment.ruleVersion(), cutoff, false)));
        assessment.contradictions().forEach(value -> target.add(new MarketThesisEvidence(
                deterministicId("contradiction", assessment.fingerprint(), value.code()),
                MarketThesisEvidenceRole.CONTRADICTING, MarketThesisAvailability.AVAILABLE,
                severity(value.code()), "TREND_CONTEXT", value.code(), null, value.message(),
                assessment.assessmentAt(), null, assessment.provider(), assessment.symbol(),
                assessment.fingerprint(), assessment.ruleVersion(), cutoff, false)));
        assessment.exclusions().forEach(value -> target.add(new MarketThesisEvidence(
                deterministicId("exclusion", assessment.fingerprint(), value.code()),
                MarketThesisEvidenceRole.MISSING, MarketThesisAvailability.UNKNOWN, null,
                "TREND_CONTEXT", value.code(), value.role().name(), value.detail(),
                assessment.assessmentAt(), null, assessment.provider(), assessment.symbol(),
                assessment.fingerprint(), assessment.ruleVersion(), cutoff, false)));
        assessment.invalidations().stream().filter(value -> invalidationFresh(assessment, value))
                .forEach(value -> target.add(new MarketThesisEvidence(
                deterministicId("invalidation", assessment.fingerprint(), value.ruleId()),
                MarketThesisEvidenceRole.CONTRADICTING, MarketThesisAvailability.AVAILABLE,
                MarketThesisContradictionSeverity.BLOCKING, "TREND_CONTEXT", value.ruleId(),
                null, "Structural invalidation is active", assessment.assessmentAt(), null,
                assessment.provider(), assessment.symbol(), assessment.fingerprint(),
                 assessment.ruleVersion(), cutoff, false)));
        trendContextProfile.roles().forEach((role, definition) -> {
            var value = assessment.timeframes().get(role);
            if (value == null) {
                addMissing(target, "TREND_CONTEXT", role.name(),
                        MarketThesisAvailability.UNAVAILABLE, assessment.assessmentAt(),
                        "Trend timeframe evidence is unavailable", definition.required());
                return;
            }
            target.add(new MarketThesisEvidence(
                    deterministicId("timeframe", assessment.fingerprint(), value.role(), value.interval()),
                    value.fresh() ? MarketThesisEvidenceRole.SUPPORTING
                            : MarketThesisEvidenceRole.MISSING,
                    value.fresh() ? MarketThesisAvailability.AVAILABLE
                            : MarketThesisAvailability.STALE, null,
                    "TREND_CONTEXT", value.role().name(), value.interval(),
                    value.fresh() ? "Trend timeframe evidence is available"
                            : "Trend timeframe evidence is stale", assessment.assessmentAt(), null,
                    assessment.provider(), assessment.symbol(), assessment.fingerprint(),
                    assessment.ruleVersion(), cutoff, definition.required()));
        });
        if (assessment.alignment() == TrendTimeframeAlignment.CONFLICTING
                || assessment.alignment() == TrendTimeframeAlignment.TRIGGER_CONTRADICTION) {
            target.add(new MarketThesisEvidence(
                    deterministicId("timeframe-conflict", assessment.fingerprint()),
                    MarketThesisEvidenceRole.CONTRADICTING, MarketThesisAvailability.AVAILABLE,
                    MarketThesisContradictionSeverity.WEAKENING, "TREND_CONTEXT",
                    assessment.alignment().name(), null,
                    "Trend timeframes are not aligned", assessment.assessmentAt(), null,
                    assessment.provider(), assessment.symbol(), assessment.fingerprint(),
                     assessment.ruleVersion(), cutoff, false));
        }
    }

    private static void addMomentumEvidence(
            List<MarketThesisEvidence> target, MomentumCapabilityContent content,
            TrendContextAssessment assessment, Instant cutoff) {
        if (content == null || content.assessment() == null) {
            addMissing(target, "MOMENTUM", "MOMENTUM_UNAVAILABLE",
                    MarketThesisAvailability.UNAVAILABLE, cutoff,
                    "Momentum analysis is unavailable", true);
            return;
        }
        MomentumAssessment momentum = content.assessment();
        if (momentum.cutoff() != null && momentum.cutoff().isAfter(cutoff)) {
            addMissing(target, "MOMENTUM", "MOMENTUM_CUTOFF_INCOMPATIBLE",
                    MarketThesisAvailability.UNKNOWN, cutoff,
                    "Momentum assessment is newer than the requested source cutoff", true);
            return;
        }
        if (content.validUntil().isBefore(cutoff)) {
            addMissing(target, "MOMENTUM", "MOMENTUM_STALE",
                    MarketThesisAvailability.STALE, cutoff,
                    "Momentum assessment is stale for the requested source cutoff", true);
            return;
        }
        if (assessment == null || assessment.direction() == TrendDirection.NEUTRAL
                || momentum.direction() == MomentumAssessment.Direction.NEUTRAL
                || momentum.direction() == MomentumAssessment.Direction.UNKNOWN) {
            addMissing(target, "MOMENTUM", "MOMENTUM_NEUTRAL",
                    MarketThesisAvailability.AVAILABLE, momentum.cutoff(),
                    "Momentum does not provide directional confirmation", true);
            return;
        }
        boolean aligned = (assessment.direction() == TrendDirection.UP
                && momentum.direction() == MomentumAssessment.Direction.UP
                && momentum.directionalPersistence().compareTo(BigDecimal.valueOf(0.5)) >= 0)
                || (assessment.direction() == TrendDirection.DOWN
                && momentum.direction() == MomentumAssessment.Direction.DOWN
                && momentum.directionalPersistence().compareTo(BigDecimal.valueOf(0.5)) >= 0);
        boolean supporting = aligned
                && momentum.acceleration() != MomentumAssessment.Acceleration.DECELERATING;
        target.add(new MarketThesisEvidence(
                deterministicId("momentum", momentum.resultFingerprint()),
                supporting ? MarketThesisEvidenceRole.SUPPORTING : MarketThesisEvidenceRole.CONTRADICTING,
                MarketThesisAvailability.AVAILABLE,
                supporting ? null : (aligned
                        ? MarketThesisContradictionSeverity.WEAKENING
                        : MarketThesisContradictionSeverity.BLOCKING),
                "MOMENTUM", momentum.direction().name(), momentum.acceleration().name(),
                content.operationalStatus(), momentum.cutoff(), null, null,
                assessment.symbol(), momentum.resultFingerprint(), momentum.policyVersion(), cutoff, true));
    }

    private static MarketThesisContradictionSeverity severity(String code) {
        String normalized = code.toUpperCase();
        return normalized.contains("BLOCK") || normalized.contains("INVALID")
                ? MarketThesisContradictionSeverity.BLOCKING
                : MarketThesisContradictionSeverity.WEAKENING;
    }

    private static boolean invalidationFresh(
            TrendContextAssessment assessment, TrendInvalidation value) {
        TrendContextRole role = value.evidence() != null
                ? value.evidence().role()
                : value.protectedLevel() == null ? null : value.protectedLevel().source().role();
        return role != null && assessment.timeframes().get(role) != null
                && assessment.timeframes().get(role).fresh();
    }

    private static void addMissing(
            List<MarketThesisEvidence> target, String source, String reference,
            MarketThesisAvailability availability, Instant observedAt, String explanation) {
        addMissing(target, source, reference, availability, observedAt, explanation,
                source.equals("TREND_CONTEXT"));
    }

    private static void addMissing(
            List<MarketThesisEvidence> target, String source, String reference,
            MarketThesisAvailability availability, Instant observedAt, String explanation,
            boolean required) {
        target.add(new MarketThesisEvidence(
                deterministicId("missing", source, reference), MarketThesisEvidenceRole.MISSING,
                availability, null, source, reference, null, explanation, observedAt, null,
                null, null, null, VERSION, observedAt, required));
    }

    private static MarketThesisScenario scenario(
            TrendContextAssessment assessment, MomentumCapabilityContent momentum,
            List<String> reasons,
            List<MarketThesisLevel> structuralLevels) {
        if (assessment == null) {
            reasons.add("TREND_CONTEXT_UNAVAILABLE");
            return null;
        }
        MarketThesisScenario.Direction direction = switch (assessment.direction()) {
            case UP -> MarketThesisScenario.Direction.LONG;
            case DOWN -> MarketThesisScenario.Direction.SHORT;
            case NEUTRAL, UNKNOWN -> {
                reasons.add("NO_DIRECTIONAL_SCENARIO");
                yield MarketThesisScenario.Direction.NONE;
            }
        };
        List<MarketThesisCondition> confirmations = new ArrayList<>(List.of(new MarketThesisCondition(
                "TREND_DIRECTIONAL", direction == MarketThesisScenario.Direction.NONE
                        ? MarketThesisConditionStatus.PENDING : MarketThesisConditionStatus.SATISFIED,
                assessment.fingerprint(), "A directional Trend Context assessment is required")));
        MomentumAssessment momentumAssessment = momentum == null ? null : momentum.assessment();
        boolean momentumConfirms = momentumAssessment != null
                && ((assessment.direction() == TrendDirection.UP
                && momentumAssessment.direction() == MomentumAssessment.Direction.UP)
                || (assessment.direction() == TrendDirection.DOWN
                && momentumAssessment.direction() == MomentumAssessment.Direction.DOWN))
                && momentumAssessment.directionalPersistence().compareTo(BigDecimal.valueOf(0.5)) >= 0
                && momentumAssessment.acceleration() != MomentumAssessment.Acceleration.DECELERATING;
        confirmations.add(new MarketThesisCondition(
                "MOMENTUM_CONFIRMATION",
                momentumConfirms
                        ? MarketThesisConditionStatus.SATISFIED : MarketThesisConditionStatus.PENDING,
                momentumAssessment == null ? "MOMENTUM_UNAVAILABLE" : momentumAssessment.resultFingerprint(),
                "Current OHLC Momentum confirmation is required"));
        List<MarketThesisCondition> invalidations = assessment.invalidations().stream()
                .map(value -> new MarketThesisCondition(
                        "INVALIDATE_" + value.ruleId(), MarketThesisConditionStatus.PENDING,
                        value.ruleId(), "Invalidate when the structural condition is met"))
                .toList();
        List<MarketThesisLevel> levels = List.copyOf(structuralLevels);
        return new MarketThesisScenario(direction,
                "Conditional scenario derived from deterministic Trend Context evidence",
                confirmations,
                invalidations.isEmpty()
                        ? List.of(new MarketThesisCondition(
                                "STRUCTURAL_INVALIDATION", MarketThesisConditionStatus.PENDING,
                                assessment.fingerprint(), "Structural invalidation evidence is required"))
                        : invalidations,
                levels);
    }

    private static MarketThesisState state(
            MarketThesisScenario scenario, List<MarketThesisEvidence> evidence,
            List<String> reasons) {
        if (scenario == null || scenario.direction() == MarketThesisScenario.Direction.NONE) {
            return MarketThesisState.NO_TRADE;
        }
        if (evidence.stream().anyMatch(value ->
                value.role() == MarketThesisEvidenceRole.CONTRADICTING
                        && value.severity() == MarketThesisContradictionSeverity.BLOCKING)) {
            reasons.add("BLOCKING_CONTRADICTION");
            return MarketThesisState.NO_TRADE;
        }
        if (evidence.stream().anyMatch(value ->
                value.role() == MarketThesisEvidenceRole.CONTRADICTING
                        && value.severity() == MarketThesisContradictionSeverity.WEAKENING
                        && "MOMENTUM".equals(value.source()))) {
            reasons.add("MOMENTUM_WEAKENING");
            return MarketThesisState.WATCH;
        }
        if (evidence.stream().anyMatch(value ->
                value.role() == MarketThesisEvidenceRole.MISSING
                        && value.required()
                        && (value.availability() == MarketThesisAvailability.STALE
                        || value.availability() == MarketThesisAvailability.UNAVAILABLE
                        || value.availability() == MarketThesisAvailability.UNKNOWN))) {
            boolean unavailable = evidence.stream().anyMatch(value -> value.required()
                    && value.role() == MarketThesisEvidenceRole.MISSING
                    && (value.availability() == MarketThesisAvailability.UNAVAILABLE
                    || value.availability() == MarketThesisAvailability.UNKNOWN));
            reasons.add(unavailable ? "REQUIRED_EVIDENCE_UNAVAILABLE" : "REQUIRED_EVIDENCE_STALE");
            return unavailable ? MarketThesisState.NO_TRADE : MarketThesisState.WATCH;
        }
        reasons.add("CONFIRMATION_SATISFIED");
        return MarketThesisState.CANDIDATE;
    }

    private static TrendContextAssessment trendAssessment(Observation observation) {
        return observation.payload()
                .filter(MarketThesisObservationPayload.class::isInstance)
                .map(MarketThesisObservationPayload.class::cast)
                .map(MarketThesisObservationPayload::trendContext)
                .map(value -> value == null ? null : value.assessment())
                .orElseGet(() -> observation.payload()
                        .filter(TrendContextObservationPayload.class::isInstance)
                        .map(TrendContextObservationPayload.class::cast)
                        .map(TrendContextObservationPayload::content)
                        .map(value -> value.assessment())
                        .orElse(null));
    }

    private static MomentumCapabilityContent momentumContent(Observation observation) {
        return observation.payload()
                .filter(MarketThesisObservationPayload.class::isInstance)
                .map(MarketThesisObservationPayload.class::cast)
                .map(MarketThesisObservationPayload::momentum)
                .orElse(null);
    }

    private static ReferencePrice referencePrice(Observation observation, Instant cutoff) {
        return observation.evidence().stream()
                .filter(value -> value.measurements() != null
                        && value.measurements().containsKey("closePrice"))
                .filter(value -> !value.observedAt().isAfter(cutoff))
                .filter(value -> observation.validUntil().isEmpty()
                        || !observation.validUntil().get().isBefore(cutoff))
                .max(Comparator.comparing(ObservationEvidence::observedAt)
                        .thenComparing(ObservationEvidence::evidenceId))
                .map(value -> new ReferencePrice(
                        value.measurements().get("closePrice"), value.observedAt()))
                .orElse(new ReferencePrice(null, null));
    }

    private static String fingerprint(
            UUID marketId, UUID analysisExecutionId, Observation observation,
            TrendContextAssessment assessment,
            MomentumCapabilityContent momentum,
            MarketThesisScenario scenario, List<String> reasons, Instant evaluatedAt,
            Instant sourceCutoff, List<MarketThesisEvidence> evidence) {
        List<String> inputs = new ArrayList<>(List.of(
                VERSION, StructuralLevelObjectiveComposer.VERSION,
                marketId.toString(), observation.id().toString(),
                Long.toString(observation.version()), observation.instrument()));
        inputs.add(String.valueOf(analysisExecutionId));
        inputs.add(String.valueOf(observation.horizon()));
        inputs.add(observation.validFrom().toString());
        inputs.add(String.valueOf(observation.validUntil()));
        inputs.add(evaluatedAt.toString());
        inputs.add(sourceCutoff.toString());
        if (assessment != null) {
            inputs.add(assessment.fingerprint());
            inputs.add(assessment.ruleVersion());
            inputs.add(assessment.profileVersion());
        }
        if (momentum != null && momentum.assessment() != null) {
            inputs.add(momentum.assessment().resultFingerprint());
            inputs.add(momentum.assessment().policyVersion());
        }
        evidence.stream().map(value -> String.join("|",
                value.evidenceId().toString(), value.role().name(), value.availability().name(),
                String.valueOf(value.severity()), value.source(), value.reference(),
                String.valueOf(value.timeframe()), value.explanation(),
                value.observedAt().toString(), String.valueOf(value.validUntil()),
                String.valueOf(value.provider()), String.valueOf(value.instrument()),
                 String.valueOf(value.sourceFingerprint()), String.valueOf(value.ruleVersion()),
                 String.valueOf(value.sourceCutoff()), Boolean.toString(value.required())))
                .sorted().forEach(inputs::add);
        inputs.add(String.valueOf(scenario));
        reasons.stream().sorted().forEach(inputs::add);
        return ArtifactFingerprint.ofInputs(inputs).value();
    }

    private static UUID deterministicId(Object... values) {
        String value = java.util.Arrays.stream(values)
                .map(String::valueOf).reduce((left, right) -> left + "|" + right).orElse("");
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private record ReferencePrice(BigDecimal price, Instant observedAt) { }
}
