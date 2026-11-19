package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.opportunity.OpportunityRankingRationale;
import com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment;

import java.util.List;

public record OpportunityRankingRationaleResponse(
        String policyVersion,
        String evidenceQualityPolicyVersion,
        EvidenceQualityAssessment.QualityStatus qualityStatus,
        List<DimensionDecisionResponse> dimensions,
        List<ReasonResponse> reasons,
        String evidenceQualityFingerprint,
        String thesisInputFingerprint,
        String orderingExplanation
) {
    public static OpportunityRankingRationaleResponse from(OpportunityRankingRationale rationale) {
        return new OpportunityRankingRationaleResponse(
                rationale.policyVersion(), rationale.evidenceQualityPolicyVersion(),
                rationale.qualityStatus(),
                rationale.dimensions().stream().map(value -> new DimensionDecisionResponse(
                        value.dimension(), value.state(), value.precedence())).toList(),
                rationale.reasons().stream().map(value -> new ReasonResponse(
                        value.code(), value.explanation())).toList(),
                rationale.evidenceQualityFingerprint(), rationale.thesisInputFingerprint(),
                "Ordered by quality status, then contradiction, applicability, completeness, "
                        + "freshness, provenance, missing-policy precedence, score, and stable opportunity identity.");
    }

    public record DimensionDecisionResponse(
            EvidenceQualityAssessment.Dimension dimension,
            EvidenceQualityAssessment.DimensionState state,
            int precedence
    ) {
    }

    public record ReasonResponse(String code, String explanation) {
    }
}
