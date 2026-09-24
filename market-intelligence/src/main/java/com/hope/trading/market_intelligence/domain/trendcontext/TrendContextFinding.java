package com.hope.trading.market_intelligence.domain.trendcontext;

import java.util.List;
import java.util.Objects;

public record TrendContextFinding(String code, String ruleId, TrendContextRole role,
                                  String message, List<TrendContextEvidenceReference> evidence) {
    public TrendContextFinding {
        code = Objects.requireNonNull(code); ruleId = Objects.requireNonNull(ruleId);
        Objects.requireNonNull(role); message = Objects.requireNonNull(message);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
    }
}
