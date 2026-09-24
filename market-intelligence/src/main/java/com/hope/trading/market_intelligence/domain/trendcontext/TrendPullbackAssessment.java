package com.hope.trading.market_intelligence.domain.trendcontext;

import java.util.List;

public record TrendPullbackAssessment(TrendDirection direction, ConfirmedSwing qualifyingSwing,
                                      List<TrendContextCandle> postPivotCandles, boolean qualified,
                                      TrendContextEvidenceReference evidence) {
    public TrendPullbackAssessment { postPivotCandles = List.copyOf(postPivotCandles == null ? List.of() : postPivotCandles); }
    public static TrendPullbackAssessment unavailable() { return new TrendPullbackAssessment(TrendDirection.UNKNOWN, null, List.of(), false, null); }
}
