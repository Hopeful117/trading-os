package com.hope.trading.market_intelligence.application.opportunity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OpportunityRankingResult(
        String policyVersion,
        List<OpportunityRankingEntry> ranked,
        List<ExcludedOpportunity> excluded
) {
    public OpportunityRankingResult {
        policyVersion = required(policyVersion, "policyVersion");
        ranked = List.copyOf(Objects.requireNonNull(ranked, "ranked"));
        excluded = List.copyOf(Objects.requireNonNull(excluded, "excluded"));
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    public record ExcludedOpportunity(UUID opportunityId, long opportunityVersion,
                                      String code, String explanation) {
        public ExcludedOpportunity {
            Objects.requireNonNull(opportunityId, "opportunityId");
            if (opportunityVersion < 1) throw new IllegalArgumentException("opportunityVersion starts at 1");
            code = required(code, "code");
            explanation = required(explanation, "explanation");
        }

        private static String required(String value, String field) {
            Objects.requireNonNull(value, field);
            if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
            return value.trim();
        }
    }
}
