package com.hope.trading.market_intelligence.domain.trendcontext;

import java.math.BigDecimal;

public record StructuralBreak(BreakStatus status, BigDecimal level, TrendContextCandle candle,
                              ProtectedLevel protectedLevel, int barsAfterBreak,
                              TrendContextCandle reclaimCandle,
                              TrendContextEvidenceReference evidence) {
    public static StructuralBreak none() {
        return new StructuralBreak(BreakStatus.NONE, null, null, null, 0, null, null);
    }
}
