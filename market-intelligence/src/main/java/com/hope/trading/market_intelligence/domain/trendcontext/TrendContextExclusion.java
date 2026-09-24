package com.hope.trading.market_intelligence.domain.trendcontext;

import java.util.Objects;

public record TrendContextExclusion(String code, TrendContextRole role, String detail,
                                    TrendContextEvidenceReference evidence) {
    public TrendContextExclusion {
        code = Objects.requireNonNull(code); Objects.requireNonNull(role); detail = Objects.requireNonNull(detail);
    }
}
