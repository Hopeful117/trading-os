package com.hope.trading.trading_core.risk.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public record RiskDay(LocalDate date, Instant startsAt, Instant endsAt) {
    public static RiskDay containing(Instant instant, String zoneName) {
        return containing(instant, zoneName, LocalTime.MIDNIGHT);
    }

    public static RiskDay containing(Instant instant, String zoneName, LocalTime resetTime) {
        ZoneId zone = ZoneId.of(zoneName);
        LocalDate localDate = instant.atZone(zone).toLocalDate();
        Instant candidate = localDate.atTime(resetTime).atZone(zone).toInstant();
        LocalDate date = instant.isBefore(candidate) ? localDate.minusDays(1) : localDate;
        Instant startsAt = date.atTime(resetTime).atZone(zone).toInstant();
        return new RiskDay(date, startsAt,
                date.plusDays(1).atTime(resetTime).atZone(zone).toInstant());
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(startsAt) && instant.isBefore(endsAt);
    }
}
