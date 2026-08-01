package com.hope.trading.trading_core.helper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public class TimeUtils {
    public static Instant startOfDay() {
        return LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }

    public static Instant endOfDay() {
        return LocalDate.now(ZoneOffset.UTC)
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }
}
