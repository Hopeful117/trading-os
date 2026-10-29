package com.hope.trading.market_intelligence.application.thesis;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

final class ThesisTimestampPrecision {
    private ThesisTimestampPrecision() {
    }

    static Instant normalize(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    static boolean notAfter(Instant value, Instant cutoff) {
        return !normalize(value).isAfter(normalize(cutoff));
    }
}
