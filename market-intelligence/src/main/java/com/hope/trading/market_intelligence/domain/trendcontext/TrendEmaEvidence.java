package com.hope.trading.market_intelligence.domain.trendcontext;

import java.math.BigDecimal;

public record TrendEmaEvidence(int period, BigDecimal current, BigDecimal slope,
                               TrendEmaSlope slopeClassification, boolean available,
                               TrendContextEvidenceReference evidence) {
    public static TrendEmaEvidence unavailable(int period) { return new TrendEmaEvidence(period, null, null, TrendEmaSlope.UNKNOWN, false, null); }
}
