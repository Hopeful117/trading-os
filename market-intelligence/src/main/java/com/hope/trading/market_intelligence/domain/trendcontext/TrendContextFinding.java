package com.hope.trading.market_intelligence.domain.trendcontext;

import java.util.List;
import java.util.Objects;

public record TrendContextFinding(String code, String ruleId, TrendContextRole role,
                                  String message, List<TrendContextEvidenceReference> evidence) {
    public TrendContextFinding {
        Objects.requireNonNull(code);
        Objects.requireNonNull(ruleId);
        Objects.requireNonNull(role);
        Objects.requireNonNull(message);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
    }
}
