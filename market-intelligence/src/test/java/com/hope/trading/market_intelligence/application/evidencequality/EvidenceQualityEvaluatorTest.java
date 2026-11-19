package com.hope.trading.market_intelligence.application.evidencequality;

import com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment.Dimension;
import com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment.DimensionState;
import com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment.QualityStatus;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisAvailability;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisContradictionSeverity;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidence;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidenceRole;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisScenario;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceQualityEvaluatorTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-19T10:00:00Z");
    private final EvidenceQualityEvaluator evaluator = new EvidenceQualityEvaluator();

    @Test
    void identicalInputsProduceIdenticalAssessment() {
        MarketThesis thesis = thesis(List.of(evidence(MarketThesisEvidenceRole.SUPPORTING,
                MarketThesisAvailability.AVAILABLE, true, "rule-v1")));

        var first = evaluator.assess(thesis);
        var second = evaluator.assess(thesis);

        assertThat(first).isEqualTo(second);
        assertThat(first.status()).isEqualTo(QualityStatus.SUPPORTED);
    }

    @Test
    void evidenceOrderDoesNotChangeFingerprint() {
        MarketThesisEvidence first = evidence(MarketThesisEvidenceRole.SUPPORTING,
                MarketThesisAvailability.AVAILABLE, true, "rule-v1");
        MarketThesisEvidence second = evidence(MarketThesisEvidenceRole.SUPPORTING,
                MarketThesisAvailability.AVAILABLE, false, "rule-v1");

        var assessmentA = evaluator.assess(thesis(List.of(first, second)));
        var assessmentB = evaluator.assess(thesis(List.of(second, first)));

        assertThat(assessmentA.inputFingerprint()).isEqualTo(assessmentB.inputFingerprint());
        assertThat(assessmentA.resultFingerprint()).isEqualTo(assessmentB.resultFingerprint());
    }

    @Test
    void staleRequiredEvidenceIsExplicitlyDegraded() {
        var assessment = evaluator.assess(thesis(List.of(evidence(MarketThesisEvidenceRole.MISSING,
                MarketThesisAvailability.STALE, true, "rule-v1"))));

        assertThat(assessment.status()).isEqualTo(QualityStatus.DEGRADED);
        assertThat(dimensions(assessment).get(Dimension.COMPLETENESS)).isEqualTo(DimensionState.MISSING);
        assertThat(dimensions(assessment).get(Dimension.FRESHNESS)).isEqualTo(DimensionState.STALE);
    }

    @Test
    void optionalUnavailableEvidenceCannotProduceSupportedAssessment() {
        var assessment = evaluator.assess(thesis(List.of(
                evidence(MarketThesisEvidenceRole.SUPPORTING,
                        MarketThesisAvailability.AVAILABLE, true, "rule-v1"),
                evidence(MarketThesisEvidenceRole.MISSING,
                        MarketThesisAvailability.UNAVAILABLE, false, "rule-v1"))));

        assertThat(assessment.status()).isEqualTo(QualityStatus.UNAVAILABLE);
        assertThat(dimensions(assessment).get(Dimension.COMPLETENESS))
                .isEqualTo(DimensionState.UNAVAILABLE);
    }

    @Test
    void validityWindowControlsFreshnessAndFingerprint() {
        UUID evidenceId = UUID.randomUUID();
        MarketThesisEvidence stale = evidenceWithValidity(evidenceId, CUTOFF.minusSeconds(1));
        MarketThesisEvidence fresh = evidenceWithValidity(evidenceId, CUTOFF.plusSeconds(1));

        var staleAssessment = evaluator.assess(thesis(List.of(stale)));
        var freshAssessment = evaluator.assess(thesis(List.of(fresh)));

        assertThat(dimensions(staleAssessment).get(Dimension.FRESHNESS))
                .isEqualTo(DimensionState.STALE);
        assertThat(staleAssessment.inputFingerprint())
                .isNotEqualTo(freshAssessment.inputFingerprint());
    }

    @Test
    void notImplementedEvidenceCannotBePositive() {
        var assessment = evaluator.assess(thesis(List.of(evidence(MarketThesisEvidenceRole.MISSING,
                MarketThesisAvailability.NOT_IMPLEMENTED, true, null))));

        assertThat(assessment.status()).isEqualTo(QualityStatus.DEGRADED);
        assertThat(dimensions(assessment).get(Dimension.MISSING_POLICY))
                .isEqualTo(DimensionState.NOT_IMPLEMENTED);
        assertThat(assessment.reasons()).extracting("code")
                .contains("POLICY_NOT_IMPLEMENTED", "REQUIRED_EVIDENCE_NOT_IMPLEMENTED");
    }

    @Test
    void blockingContradictionBlocksAssessment() {
        var assessment = evaluator.assess(thesis(MarketThesisState.NO_TRADE, List.of(new MarketThesisEvidence(
                UUID.randomUUID(), MarketThesisEvidenceRole.CONTRADICTING,
                MarketThesisAvailability.AVAILABLE, MarketThesisContradictionSeverity.BLOCKING,
                "TREND", "contradiction", "H1", "blocking contradiction", CUTOFF,
                null, "provider", "EURUSD", "source-fingerprint", "rule-v1", CUTOFF, true))));

        assertThat(assessment.status()).isEqualTo(QualityStatus.BLOCKED);
        assertThat(dimensions(assessment).get(Dimension.CONTRADICTION))
                .isEqualTo(DimensionState.CONTRADICTED);
    }

    private static Map<Dimension, DimensionState> dimensions(
            com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment assessment) {
        return assessment.dimensions().stream().collect(Collectors.toMap(
                com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment.DimensionAssessment::dimension,
                com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment.DimensionAssessment::state));
    }

    private static MarketThesis thesis(List<MarketThesisEvidence> evidence) {
        return thesis(MarketThesisState.CANDIDATE, evidence);
    }

    private static MarketThesis thesis(MarketThesisState state, List<MarketThesisEvidence> evidence) {
        return new MarketThesis(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"), 1,
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "EURUSD", state, state == MarketThesisState.CANDIDATE
                        ? new MarketThesisScenario(MarketThesisScenario.Direction.LONG,
                        "test", "confirm", "invalidate", "level") : null,
                evidence, List.of("test"),
                CUTOFF, CUTOFF, CUTOFF, CUTOFF.plusSeconds(1800), UUID.randomUUID(),
                "composer-v1", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", null);
    }

    private static MarketThesisEvidence evidence(MarketThesisEvidenceRole role,
                                                 MarketThesisAvailability availability,
                                                 boolean required, String ruleVersion) {
        return new MarketThesisEvidence(UUID.randomUUID(), role, availability, null,
                "TEST", "reference", "H1", "test evidence", CUTOFF, CUTOFF.plusSeconds(1800),
                "provider", "EURUSD", "source-fingerprint", ruleVersion, CUTOFF, required);
    }

    private static MarketThesisEvidence evidenceWithValidity(UUID evidenceId, Instant validUntil) {
        return new MarketThesisEvidence(evidenceId, MarketThesisEvidenceRole.SUPPORTING,
                MarketThesisAvailability.AVAILABLE, null, "TEST", "reference", "H1",
                "test evidence", CUTOFF.minusSeconds(3600), validUntil, "provider", "EURUSD",
                "source-fingerprint", "rule-v1", CUTOFF, true);
    }
}
