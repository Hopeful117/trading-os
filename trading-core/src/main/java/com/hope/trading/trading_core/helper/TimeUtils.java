package com.hope.trading.trading_core.helper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class TimeUtils {
    public static Instant startOfDay() {
        return LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();
    }

    public static Instant endOfDay() {
        return LocalDate.now()
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();
    }
}
