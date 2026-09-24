package com.hope.trading.market_intelligence.domain.trendcontext;

import java.math.BigDecimal;

public record TrendAtrEvidence(int period, BigDecimal current, BigDecimal baseline,
                               BigDecimal ratio, boolean abnormal, boolean available,
                               TrendContextEvidenceReference evidence) {
    public static TrendAtrEvidence unavailable(int period) { return new TrendAtrEvidence(period, null, null, null, false, false, null); }
}
