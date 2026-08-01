package com.hope.trading.trading_core.risk.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public record RiskDay(LocalDate date, Instant startsAt, Instant endsAt) {
    public static RiskDay containing(Instant instant, String zoneName) {
        ZoneId zone = ZoneId.of(zoneName);
        LocalDate date = instant.atZone(zone).toLocalDate();
        return new RiskDay(date, date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant());
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(startsAt) && instant.isBefore(endsAt);
    }
}
