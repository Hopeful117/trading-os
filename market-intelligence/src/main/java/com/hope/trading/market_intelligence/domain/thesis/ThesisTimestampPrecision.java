package com.hope.trading.market_intelligence.domain.thesis;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class ThesisTimestampPrecision {
    private ThesisTimestampPrecision() {
    }

    public static Instant normalize(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    public static boolean notAfter(Instant value, Instant cutoff) {
        return !normalize(value).isAfter(normalize(cutoff));
    }
}
