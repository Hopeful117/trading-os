package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.application.evidencequality.EvidenceQualityEvaluator;
import com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment;
import com.hope.trading.market_intelligence.domain.opportunity.MarketThesisReference;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityScore;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityStatus;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisAvailability;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidence;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidenceRole;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisScenario;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceBasedOpportunityRankingPolicyTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-19T10:00:00Z");
    private static final EvidenceQualityEvaluator QUALITY = new EvidenceQualityEvaluator();
    private final EvidenceBasedOpportunityRankingPolicy policy =
            new EvidenceBasedOpportunityRankingPolicy();

    @Test
    void supportedEvidenceRanksAboveDegradedEvidenceBeforeBusinessPriority() {
        MarketThesis supported = thesis(evidence(MarketThesisEvidenceRole.SUPPORTING,
                MarketThesisAvailability.AVAILABLE, true));
        MarketThesis degraded = thesis(evidence(MarketThesisEvidenceRole.MISSING,
                MarketThesisAvailability.STALE, true));

        OpportunityRankingEntry lowScoreSupported = policy.evaluate(candidate(
                "11111111-1111-1111-1111-111111111111", "10", supported));
        OpportunityRankingEntry highScoreDegraded = policy.evaluate(candidate(
                "22222222-2222-2222-2222-222222222222", "90", degraded));

        assertThat(policy.comparator().compare(lowScoreSupported, highScoreDegraded)).isNegative();
        assertThat(lowScoreSupported.rationale().policyVersion())
                .isEqualTo(EvidenceBasedOpportunityRankingPolicy.VERSION);
        assertThat(lowScoreSupported.rationale().evidenceQualityPolicyVersion())
                .isEqualTo(EvidenceQualityAssessment.POLICY_VERSION);
        assertThat(lowScoreSupported.rationale().evidenceQualityFingerprint()).isNotBlank();
        assertThat(lowScoreSupported.rationale().reasons())
                .allSatisfy(reason -> assertThat(reason.explanation()).isNotBlank());
    }

    @Test
    void identicalSemanticInputsHaveStableOrderIndependentResults() {
        MarketThesis thesis = thesis(evidence(MarketThesisEvidenceRole.SUPPORTING,
                MarketThesisAvailability.AVAILABLE, true));
        OpportunityRankingCandidate first = candidate(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "50", thesis);
        OpportunityRankingCandidate second = candidate(
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "50", thesis);

        List<OpportunityRankingEntry> firstOrder = policyOrder(List.of(first, second));
        List<OpportunityRankingEntry> secondOrder = policyOrder(List.of(second, first));

        assertThat(firstOrder).extracting(value -> value.candidate().opportunity().id().value())
                .containsExactlyElementsOf(secondOrder.stream()
                        .map(value -> value.candidate().opportunity().id().value()).toList());
        assertThat(firstOrder.get(0).rationale()).isEqualTo(secondOrder.get(0).rationale());
    }

    @Test
    void policyDoesNotMutateTheOpportunity() {
        MarketThesis thesis = thesis(evidence(MarketThesisEvidenceRole.SUPPORTING,
                MarketThesisAvailability.AVAILABLE, true));
        TradingOpportunity opportunity = candidate(
                "cccccccc-cccc-cccc-cccc-cccccccccccc", "50", thesis).opportunity();

        policy.evaluate(candidate(
                opportunity.id().value().toString(), opportunity.score().value().toPlainString(), thesis));

        assertThat(opportunity.status()).isEqualTo(OpportunityStatus.ACTIVE);
        assertThat(opportunity.score().value()).isEqualByComparingTo("50.00");
    }

    @Test
    void everyNonSatisfiedDimensionStateRanksBelowSatisfiedForThatDimension() {
        for (EvidenceQualityAssessment.Dimension dimension : EvidenceQualityAssessment.Dimension.values()) {
            OpportunityRankingEntry satisfied = policy.evaluate(candidateWithAssessment(
                    "dddddddd-dddd-dddd-dddd-dddddddddddd", assessment(dimension,
                            EvidenceQualityAssessment.DimensionState.SATISFIED)));
            for (EvidenceQualityAssessment.DimensionState state
                    : EnumSet.complementOf(EnumSet.of(EvidenceQualityAssessment.DimensionState.SATISFIED))) {
                OpportunityRankingEntry weaker = policy.evaluate(candidateWithAssessment(
                        UUID.randomUUID().toString(), assessment(dimension, state)));

                assertThat(policy.comparator().compare(satisfied, weaker))
                        .as("dimension=%s state=%s", dimension, state)
                        .isNegative();
            }
        }
    }

    @Test
    void dimensionStatesFollowTheApprovedLexicographicOrder() {
        for (EvidenceQualityAssessment.Dimension dimension : EvidenceQualityAssessment.Dimension.values()) {
            List<EvidenceQualityAssessment.DimensionState> expected = new ArrayList<>(List.of(
                    EvidenceQualityAssessment.DimensionState.SATISFIED,
                    EvidenceQualityAssessment.DimensionState.PARTIAL,
                    EvidenceQualityAssessment.DimensionState.MISSING,
                    EvidenceQualityAssessment.DimensionState.STALE,
                    EvidenceQualityAssessment.DimensionState.UNAVAILABLE,
                    EvidenceQualityAssessment.DimensionState.CONTRADICTED,
                    EvidenceQualityAssessment.DimensionState.NOT_IMPLEMENTED));
            if (dimension == EvidenceQualityAssessment.Dimension.APPLICABILITY) {
                expected.add(1, EvidenceQualityAssessment.DimensionState.NOT_APPLICABLE);
            } else {
                expected.add(EvidenceQualityAssessment.DimensionState.NOT_APPLICABLE);
            }

            for (int index = 0; index < expected.size(); index++) {
                for (int next = index + 1; next < expected.size(); next++) {
                    EvidenceQualityAssessment.DimensionState first = expected.get(index);
                    EvidenceQualityAssessment.DimensionState second = expected.get(next);
                    int firstPrecedence = precedence(dimension, first);
                    int secondPrecedence = precedence(dimension, second);
                    if (firstPrecedence > secondPrecedence) {
                        OpportunityRankingEntry higher = policy.evaluate(candidateWithAssessment(
                                "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee", assessment(dimension, first)));
                        OpportunityRankingEntry lower = policy.evaluate(candidateWithAssessment(
                                "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee", assessment(dimension, second)));

                        assertThat(policy.comparator().compare(higher, lower))
                                .as("dimension=%s first=%s second=%s", dimension, first, second)
                                .isNegative();
                    }
                }
            }
        }
    }

    @Test
    void qualityStatusesHaveExplicitPrecedence() {
        OpportunityRankingEntry supported = entryWithStatus(EvidenceQualityAssessment.QualityStatus.SUPPORTED);
        OpportunityRankingEntry degraded = entryWithStatus(EvidenceQualityAssessment.QualityStatus.DEGRADED);
        OpportunityRankingEntry unavailable = entryWithStatus(EvidenceQualityAssessment.QualityStatus.UNAVAILABLE);
        OpportunityRankingEntry blocked = entryWithStatus(EvidenceQualityAssessment.QualityStatus.BLOCKED);

        assertThat(policy.comparator().compare(supported, degraded)).isNegative();
        assertThat(policy.comparator().compare(degraded, unavailable)).isNegative();
        assertThat(policy.comparator().compare(unavailable, blocked)).isNegative();
    }

    private List<OpportunityRankingEntry> policyOrder(List<OpportunityRankingCandidate> candidates) {
        return candidates.stream().map(policy::evaluate).sorted(policy.comparator()).toList();
    }

    private static OpportunityRankingCandidate candidate(String id, String score, MarketThesis thesis) {
        MarketThesisReference reference = new MarketThesisReference(thesis.id(), thesis.version());
        TradingOpportunity opportunity = OpportunityTestFixtures.opportunity(
                new OpportunityId(UUID.fromString(id)), 1, OpportunityStatus.ACTIVE,
                new OpportunityScore(new BigDecimal(score)), CUTOFF, reference);
        return new OpportunityRankingCandidate(opportunity, reference,
                thesis.evidenceQuality().orElseThrow(), thesis.inputFingerprint());
    }

    private static OpportunityRankingCandidate candidateWithAssessment(String id,
                                                                         EvidenceQualityAssessment assessment) {
        MarketThesis thesis = thesis(evidence(MarketThesisEvidenceRole.SUPPORTING,
                MarketThesisAvailability.AVAILABLE, true));
        MarketThesisReference reference = new MarketThesisReference(thesis.id(), thesis.version());
        TradingOpportunity opportunity = OpportunityTestFixtures.opportunity(
                new OpportunityId(UUID.fromString(id)), 1, OpportunityStatus.ACTIVE,
                new OpportunityScore(new BigDecimal("50")), CUTOFF, reference);
        return new OpportunityRankingCandidate(opportunity, reference, assessment, thesis.inputFingerprint());
    }

    private OpportunityRankingEntry entryWithStatus(EvidenceQualityAssessment.QualityStatus status) {
        List<EvidenceQualityAssessment.DimensionAssessment> dimensions =
                List.of(new EvidenceQualityAssessment.DimensionAssessment(
                        EvidenceQualityAssessment.Dimension.COMPLETENESS,
                        EvidenceQualityAssessment.DimensionState.SATISFIED, List.of("test")));
        EvidenceQualityAssessment assessment = new EvidenceQualityAssessment(
                EvidenceQualityAssessment.POLICY_VERSION, CUTOFF, CUTOFF, status, dimensions,
                List.of(new EvidenceQualityAssessment.QualityReason("TEST_REASON", "test explanation")),
                List.of(), "b".repeat(64), "c".repeat(64));
        return policy.evaluate(candidateWithAssessment(UUID.randomUUID().toString(), assessment));
    }

    private static EvidenceQualityAssessment assessment(
            EvidenceQualityAssessment.Dimension target,
            EvidenceQualityAssessment.DimensionState state) {
        List<EvidenceQualityAssessment.DimensionAssessment> dimensions = new ArrayList<>();
        for (EvidenceQualityAssessment.Dimension dimension : EvidenceQualityAssessment.Dimension.values()) {
            dimensions.add(new EvidenceQualityAssessment.DimensionAssessment(
                    dimension, dimension == target ? state : EvidenceQualityAssessment.DimensionState.SATISFIED,
                    List.of("test")));
        }
        return new EvidenceQualityAssessment(
                EvidenceQualityAssessment.POLICY_VERSION, CUTOFF, CUTOFF,
                EvidenceQualityAssessment.QualityStatus.SUPPORTED, dimensions,
                List.of(new EvidenceQualityAssessment.QualityReason("TEST_REASON", "test explanation")),
                List.of(), "d".repeat(64), "e".repeat(64));
    }

    private static int precedence(EvidenceQualityAssessment.Dimension dimension,
                                  EvidenceQualityAssessment.DimensionState state) {
        return switch (state) {
            case SATISFIED -> 6;
            case NOT_APPLICABLE -> dimension == EvidenceQualityAssessment.Dimension.APPLICABILITY ? 5 : 0;
            case PARTIAL -> 4;
            case MISSING, STALE -> 2;
            case UNAVAILABLE -> 1;
            case CONTRADICTED, NOT_IMPLEMENTED -> 0;
        };
    }

    private static MarketThesis thesis(MarketThesisEvidence evidence) {
        MarketThesis value = new MarketThesis(
                UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), "BTC/EUR",
                MarketThesisState.CANDIDATE,
                new MarketThesisScenario(MarketThesisScenario.Direction.LONG,
                        "test", "confirm", "invalidate", "level"),
                List.of(evidence), List.of("test"), CUTOFF, CUTOFF, CUTOFF,
                CUTOFF.plusSeconds(1800), UUID.randomUUID(), "composer-v1",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", null);
        return new MarketThesis(value.id(), value.lineageId(), value.version(), value.marketId(),
                value.instrument(), value.state(), value.scenario().orElseThrow(), value.evidence(),
                value.reasons(), value.evaluatedAt(), value.sourceCutoff(), value.validFrom(),
                value.validUntil().orElseThrow(), value.analysisExecutionId().orElse(null),
                value.composerVersion(), value.inputFingerprint(), value.supersedes().orElse(null),
                QUALITY.assess(value));
    }

    private static MarketThesisEvidence evidence(MarketThesisEvidenceRole role,
                                                 MarketThesisAvailability availability,
                                                 boolean required) {
        return new MarketThesisEvidence(UUID.randomUUID(), role, availability, null,
                "TEST", "reference", "H1", "test evidence", CUTOFF, CUTOFF.plusSeconds(1800),
                "provider", "BTC/EUR", "source-fingerprint", "rule-v1", CUTOFF, required);
    }
}
