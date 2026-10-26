package com.hope.trading.market_data.ctrader;

import java.time.Duration;
import java.time.Instant;

public final class CTraderTrendbarSemantics {
    private CTraderTrendbarSemantics() { }

    public static boolean isClosed(Instant openTime, Duration interval, Instant cutoff) {
        if (openTime == null || interval == null || cutoff == null || interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("openTime, positive interval and cutoff are required");
        }
        return !openTime.plus(interval).isAfter(cutoff);
    }
}
