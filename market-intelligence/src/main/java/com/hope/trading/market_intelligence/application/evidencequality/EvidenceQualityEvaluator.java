package com.hope.trading.market_intelligence.application.evidencequality;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisAvailability;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidence;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidenceRole;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment.Dimension;
import static com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment.DimensionAssessment;
import static com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment.DimensionState;
import static com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment.QualityReason;
import static com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment.QualityStatus;
import static com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment.EvidenceProvenance;

/** Pure assessment over an already assembled Market Thesis. */
public final class EvidenceQualityEvaluator {
    public EvidenceQualityAssessment assess(MarketThesis thesis) {
        Objects.requireNonNull(thesis, "thesis");
        return assess(thesis, thesis.sourceCutoff(), Optional.empty());
    }

    public EvidenceQualityAssessment assess(MarketThesis thesis, Instant sourceCutoff) {
        return assess(thesis, sourceCutoff, Optional.empty());
    }

    public EvidenceQualityAssessment assess(MarketThesis thesis, StrategyMatch strategyMatch) {
        return assess(thesis, thesis.sourceCutoff(), Optional.of(strategyMatch));
    }

    private EvidenceQualityAssessment assess(MarketThesis thesis, Instant sourceCutoff,
                                             Optional<StrategyMatch> strategyMatch) {
        Objects.requireNonNull(thesis, "thesis");
        Objects.requireNonNull(sourceCutoff, "sourceCutoff");
        List<MarketThesisEvidence> evidence = thesis.evidence().stream()
                .sorted(Comparator.comparing(value -> value.evidenceId().toString()))
                .toList();
        List<QualityReason> reasons = new ArrayList<>();
        List<DimensionAssessment> dimensions = List.of(
                completeness(evidence, reasons),
                freshness(evidence, sourceCutoff, reasons),
                provenance(evidence, reasons),
                contradiction(evidence, reasons),
                applicability(thesis, strategyMatch, reasons),
                missingPolicy(evidence, reasons));
        QualityStatus status = status(dimensions);
        String inputFingerprint = ArtifactFingerprint.ofInputs(evidence.stream()
                .map(this::identity)
                .toList()).value();
        String resultFingerprint = ArtifactFingerprint.ofParameters(java.util.Map.of(
                "policy", EvidenceQualityAssessment.POLICY_VERSION,
                "thesis", thesis.inputFingerprint(),
                "cutoff", sourceCutoff,
                "status", status,
                "dimensions", dimensions,
                "reasons", reasons,
                "provenance", provenance(evidence),
                "strategy", strategyIdentity(strategyMatch))).value();
        return new EvidenceQualityAssessment(EvidenceQualityAssessment.POLICY_VERSION,
                thesis.evaluatedAt(), sourceCutoff, status, dimensions, reasons,
                provenance(evidence), inputFingerprint, resultFingerprint);
    }

    private DimensionAssessment completeness(List<MarketThesisEvidence> evidence,
                                             List<QualityReason> reasons) {
        List<MarketThesisEvidence> required = evidence.stream().filter(MarketThesisEvidence::required).toList();
        List<String> references = references(evidence);
        if (required.isEmpty()) {
            reasons.add(reason("NO_REQUIRED_EVIDENCE", "No required evidence was declared"));
            return dimension(Dimension.COMPLETENESS, DimensionState.PARTIAL, references);
        }
        if (required.stream().anyMatch(value -> value.availability() == MarketThesisAvailability.NOT_IMPLEMENTED)) {
            reasons.add(reason("REQUIRED_EVIDENCE_NOT_IMPLEMENTED", "Required evidence is not implemented"));
            return dimension(Dimension.COMPLETENESS, DimensionState.NOT_IMPLEMENTED, references);
        }
        if (required.stream().anyMatch(value -> value.role() == MarketThesisEvidenceRole.MISSING)) {
            reasons.add(reason("REQUIRED_EVIDENCE_MISSING", "Required evidence is missing"));
            return dimension(Dimension.COMPLETENESS, DimensionState.MISSING, references);
        }
        if (required.stream().anyMatch(value -> value.availability() != MarketThesisAvailability.AVAILABLE)) {
            reasons.add(reason("REQUIRED_EVIDENCE_UNAVAILABLE", "Required evidence is not available"));
            return dimension(Dimension.COMPLETENESS, DimensionState.UNAVAILABLE, references);
        }
        List<MarketThesisEvidence> optionalNegative = evidence.stream()
                .filter(value -> !value.required() && value.availability() != MarketThesisAvailability.AVAILABLE)
                .toList();
        if (!optionalNegative.isEmpty()) {
            DimensionState state = optionalNegative.stream().anyMatch(value ->
                    value.availability() == MarketThesisAvailability.NOT_IMPLEMENTED)
                    ? DimensionState.NOT_IMPLEMENTED
                    : optionalNegative.stream().anyMatch(value ->
                    value.availability() == MarketThesisAvailability.UNAVAILABLE)
                    ? DimensionState.UNAVAILABLE : DimensionState.PARTIAL;
            reasons.add(reason("OPTIONAL_EVIDENCE_INCOMPLETE", "Optional evidence is incomplete"));
            return dimension(Dimension.COMPLETENESS, state, references(optionalNegative));
        }
        return dimension(Dimension.COMPLETENESS, DimensionState.SATISFIED, references);
    }

    private DimensionAssessment freshness(List<MarketThesisEvidence> evidence, Instant cutoff,
                                          List<QualityReason> reasons) {
        List<MarketThesisEvidence> stale = evidence.stream()
                .filter(value -> value.availability() == MarketThesisAvailability.STALE
                        || value.validUntil() != null && value.validUntil().isBefore(cutoff)
                        || value.observedAt().isAfter(cutoff))
                .toList();
        if (!stale.isEmpty()) {
            reasons.add(reason("EVIDENCE_STALE", "Evidence is stale at the source cutoff"));
            return dimension(Dimension.FRESHNESS, DimensionState.STALE, references(stale));
        }
        List<MarketThesisEvidence> future = evidence.stream()
                .filter(value -> value.sourceCutoff() != null && value.sourceCutoff().isAfter(cutoff))
                .toList();
        if (!future.isEmpty()) {
            reasons.add(reason("EVIDENCE_AFTER_CUTOFF", "Evidence is newer than the evaluation cutoff"));
            return dimension(Dimension.FRESHNESS, DimensionState.STALE, references(future));
        }
        return dimension(Dimension.FRESHNESS, DimensionState.SATISFIED, references(evidence));
    }

    private DimensionAssessment provenance(List<MarketThesisEvidence> evidence,
                                           List<QualityReason> reasons) {
        List<MarketThesisEvidence> incomplete = evidence.stream()
                .filter(value -> value.required() && (blank(value.sourceFingerprint())
                        || blank(value.ruleVersion())))
                .toList();
        if (!incomplete.isEmpty()) {
            reasons.add(reason("EVIDENCE_PROVENANCE_INCOMPLETE", "Required evidence lacks source or rule provenance"));
            return dimension(Dimension.PROVENANCE, DimensionState.PARTIAL, references(incomplete));
        }
        return dimension(Dimension.PROVENANCE, DimensionState.SATISFIED, references(evidence));
    }

    private DimensionAssessment contradiction(List<MarketThesisEvidence> evidence,
                                              List<QualityReason> reasons) {
        List<MarketThesisEvidence> blocking = evidence.stream()
                .filter(value -> value.role() == MarketThesisEvidenceRole.CONTRADICTING
                        && value.severity() != null
                        && value.severity().name().equals("BLOCKING"))
                .toList();
        if (!blocking.isEmpty()) {
            reasons.add(reason("BLOCKING_CONTRADICTION", "Blocking contradictory evidence is present"));
            return dimension(Dimension.CONTRADICTION, DimensionState.CONTRADICTED, references(blocking));
        }
        List<MarketThesisEvidence> weakening = evidence.stream()
                .filter(value -> value.role() == MarketThesisEvidenceRole.CONTRADICTING)
                .toList();
        if (!weakening.isEmpty()) {
            reasons.add(reason("WEAKENING_CONTRADICTION", "Contradictory evidence weakens the assessment"));
            return dimension(Dimension.CONTRADICTION, DimensionState.PARTIAL, references(weakening));
        }
        return dimension(Dimension.CONTRADICTION, DimensionState.SATISFIED, references(evidence));
    }

    private DimensionAssessment applicability(MarketThesis thesis, Optional<StrategyMatch> strategyMatch,
                                              List<QualityReason> reasons) {
        if (strategyMatch.isEmpty()) {
            reasons.add(reason("THESIS_NOT_APPLICABLE", "The thesis does not declare an applicable candidate"));
            return dimension(Dimension.APPLICABILITY, DimensionState.NOT_APPLICABLE, List.of());
        }
        StrategyMatch match = strategyMatch.orElseThrow();
        if (!thesis.marketId().equals(match.marketId())) {
            reasons.add(reason("STRATEGY_MARKET_MISMATCH", "Strategy evidence belongs to a different market"));
            return dimension(Dimension.APPLICABILITY, DimensionState.PARTIAL,
                    List.of(match.matchId().toString()));
        }
        return dimension(Dimension.APPLICABILITY, DimensionState.SATISFIED,
                List.of(match.matchId().toString()));
    }

    private DimensionAssessment missingPolicy(List<MarketThesisEvidence> evidence,
                                              List<QualityReason> reasons) {
        List<MarketThesisEvidence> notImplemented = evidence.stream()
                .filter(value -> value.availability() == MarketThesisAvailability.NOT_IMPLEMENTED)
                .toList();
        if (!notImplemented.isEmpty()) {
            reasons.add(reason("POLICY_NOT_IMPLEMENTED", "One or more evidence policies are not implemented"));
            return dimension(Dimension.MISSING_POLICY, DimensionState.NOT_IMPLEMENTED, references(notImplemented));
        }
        return dimension(Dimension.MISSING_POLICY, DimensionState.SATISFIED, references(evidence));
    }

    private static QualityStatus status(List<DimensionAssessment> dimensions) {
        if (dimensions.stream().anyMatch(value -> value.state() == DimensionState.CONTRADICTED)) {
            return QualityStatus.BLOCKED;
        }
        if (dimensions.stream().allMatch(value -> value.state() == DimensionState.SATISFIED
                || value.state() == DimensionState.NOT_APPLICABLE)) {
            return QualityStatus.SUPPORTED;
        }
        if (dimensions.stream().anyMatch(value -> value.state() == DimensionState.UNAVAILABLE)) {
            return QualityStatus.UNAVAILABLE;
        }
        return QualityStatus.DEGRADED;
    }

    private DimensionAssessment dimension(Dimension dimension, DimensionState state,
                                          List<String> references) {
        return new DimensionAssessment(dimension, state, references);
    }

    private List<String> references(List<MarketThesisEvidence> evidence) {
        return evidence.stream().map(value -> value.evidenceId().toString()).sorted().toList();
    }

    private String identity(MarketThesisEvidence value) {
        return ArtifactFingerprint.ofInputs(List.of(
                String.valueOf(value.evidenceId()), String.valueOf(value.role()),
                String.valueOf(value.availability()), String.valueOf(value.severity()),
                value.source(), value.reference(), String.valueOf(value.timeframe()),
                String.valueOf(value.observedAt()), String.valueOf(value.sourceCutoff()),
                String.valueOf(value.validUntil()),
                String.valueOf(value.provider()), String.valueOf(value.instrument()),
                String.valueOf(value.sourceFingerprint()), String.valueOf(value.ruleVersion()),
                String.valueOf(value.required()))).value();
    }

    private List<EvidenceProvenance> provenance(List<MarketThesisEvidence> evidence) {
        return evidence.stream().map(value -> new EvidenceProvenance(
                value.evidenceId().toString(), value.source(), value.provider(), value.instrument(),
                value.timeframe(), value.observedAt(), value.validUntil(), value.sourceCutoff(),
                value.sourceFingerprint(), value.ruleVersion())).toList();
    }

    private static String strategyIdentity(Optional<StrategyMatch> strategyMatch) {
        return strategyMatch.map(value -> value.matchId() + ":" + value.strategyId()
                + ":" + value.strategyVersion() + ":" + value.contextDigest()).orElse("none");
    }

    private static QualityReason reason(String code, String explanation) {
        return new QualityReason(code, explanation);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
