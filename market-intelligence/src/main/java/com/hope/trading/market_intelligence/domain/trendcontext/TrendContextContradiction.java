package com.hope.trading.market_intelligence.domain.trendcontext;

import java.util.List;

public record TrendContextContradiction(String code, String message,
                                        List<TrendContextEvidenceReference> evidence) {
    public TrendContextContradiction {
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
    }
}
