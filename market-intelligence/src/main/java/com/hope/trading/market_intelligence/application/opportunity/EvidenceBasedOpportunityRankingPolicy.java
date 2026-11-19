package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class EvidenceBasedOpportunityRankingPolicy implements OpportunityRankingPolicy {
    public static final String VERSION = "opportunity-ranking/v1";

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public OpportunityRankingEntry evaluate(OpportunityRankingCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        EvidenceQualityAssessment quality = candidate.evidenceQuality();
        List<OpportunityRankingRationale.DimensionDecision> dimensions = quality.dimensions().stream()
                .map(value -> new OpportunityRankingRationale.DimensionDecision(
                        value.dimension(), value.state(), precedence(value.dimension(), value.state())))
                .toList();
        OpportunityRankingRationale rationale = new OpportunityRankingRationale(
                VERSION, quality.policyVersion(), quality.status(), dimensions,
                quality.reasons().stream()
                        .map(reason -> new OpportunityRankingRationale.Reason(
                                reason.code(), reason.explanation()))
                        .sorted(Comparator.comparing(OpportunityRankingRationale.Reason::code))
                        .toList(),
                quality.resultFingerprint(), candidate.thesisInputFingerprint());
        return new OpportunityRankingEntry(candidate, rationale);
    }

    @Override
    public Comparator<OpportunityRankingEntry> comparator() {
        return Comparator
                .comparingInt((OpportunityRankingEntry value) -> qualityRank(value.rationale().qualityStatus()))
                .reversed()
                .thenComparing(dimensionComparator(EvidenceQualityAssessment.Dimension.CONTRADICTION))
                .thenComparing(dimensionComparator(EvidenceQualityAssessment.Dimension.APPLICABILITY))
                .thenComparing(dimensionComparator(EvidenceQualityAssessment.Dimension.COMPLETENESS))
                .thenComparing(dimensionComparator(EvidenceQualityAssessment.Dimension.FRESHNESS))
                .thenComparing(dimensionComparator(EvidenceQualityAssessment.Dimension.PROVENANCE))
                .thenComparing(dimensionComparator(EvidenceQualityAssessment.Dimension.MISSING_POLICY))
                .thenComparing(value -> value.candidate().opportunity().score(), Comparator.reverseOrder())
                .thenComparing(value -> value.candidate().opportunity().id().value())
                .thenComparingLong(value -> value.candidate().opportunity().version().value());
    }

    private Comparator<OpportunityRankingEntry> dimensionComparator(
            EvidenceQualityAssessment.Dimension dimension) {
        return Comparator.comparingInt((OpportunityRankingEntry value) -> value.rationale().dimensions().stream()
                        .filter(item -> item.dimension() == dimension)
                        .mapToInt(OpportunityRankingRationale.DimensionDecision::precedence)
                        .findFirst().orElse(0))
                .reversed();
    }

    private static int qualityRank(EvidenceQualityAssessment.QualityStatus status) {
        return switch (status) {
            case SUPPORTED -> 4;
            case DEGRADED -> 3;
            case UNAVAILABLE -> 2;
            case BLOCKED -> 1;
        };
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
}
