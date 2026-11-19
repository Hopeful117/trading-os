package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.opportunity.OpportunityRankingResult;

public record OpportunityRankingResponse(
        String policyVersion,
        Outcome outcome,
        Integer position,
        OpportunityRankingRationaleResponse rationale,
        String exclusionCode,
        String exclusionExplanation
) {
    public enum Outcome {
        RANKED,
        EXCLUDED
    }

    public static OpportunityRankingResponse ranked(
            String policyVersion, int position, OpportunityRankingRationaleResponse rationale) {
        return new OpportunityRankingResponse(
                policyVersion, Outcome.RANKED, position, rationale, null, null);
    }

    public static OpportunityRankingResponse excluded(
            String policyVersion, OpportunityRankingResult.ExcludedOpportunity excluded) {
        return new OpportunityRankingResponse(
                policyVersion, Outcome.EXCLUDED, null, null,
                excluded.code(), excluded.explanation());
    }
}
