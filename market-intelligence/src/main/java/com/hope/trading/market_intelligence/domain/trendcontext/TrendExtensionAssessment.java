package com.hope.trading.market_intelligence.domain.trendcontext;

import java.math.BigDecimal;

public record TrendExtensionAssessment(boolean available, TrendDirection direction,
                                       BigDecimal distance, BigDecimal atrMultiple, boolean extended,
                                       TrendContextEvidenceReference evidence) {
    public static TrendExtensionAssessment unavailable() { return new TrendExtensionAssessment(false, TrendDirection.UNKNOWN, null, null, false, null); }
}
