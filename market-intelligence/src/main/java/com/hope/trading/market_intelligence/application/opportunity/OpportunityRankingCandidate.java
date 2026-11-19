package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment;
import com.hope.trading.market_intelligence.domain.opportunity.MarketThesisReference;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;

import java.util.Objects;

public record OpportunityRankingCandidate(
        TradingOpportunity opportunity,
        MarketThesisReference thesisReference,
        EvidenceQualityAssessment evidenceQuality,
        String thesisInputFingerprint
) {
    public OpportunityRankingCandidate {
        Objects.requireNonNull(opportunity, "opportunity");
        Objects.requireNonNull(thesisReference, "thesisReference");
        Objects.requireNonNull(evidenceQuality, "evidenceQuality");
        thesisInputFingerprint = required(thesisInputFingerprint, "thesisInputFingerprint");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
