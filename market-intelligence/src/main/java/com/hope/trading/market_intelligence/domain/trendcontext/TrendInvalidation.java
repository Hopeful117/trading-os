package com.hope.trading.market_intelligence.domain.trendcontext;

import java.math.BigDecimal;

public record TrendInvalidation(String ruleId, TrendDirection thesisDirection,
                                ProtectedLevel protectedLevel, TrendContextCandle candle,
                                BigDecimal level, TrendContextEvidenceReference evidence) { }
