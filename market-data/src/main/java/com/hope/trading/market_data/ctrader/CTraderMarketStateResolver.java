package com.hope.trading.market_data.ctrader;

import com.hope.trading.market_data.helper.MarketClosureReason;
import com.hope.trading.market_data.helper.TradingStatus;
import com.hope.trading.market_data.model.MarketState;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Resolves scheduled symbol eligibility without treating a quote as execution evidence. */
final class CTraderMarketStateResolver {
    private static final long SECONDS_PER_WEEK = 7 * 24 * 60 * 60L;
    private final Clock clock;

    CTraderMarketStateResolver(Clock clock) {
        this.clock = clock;
    }

    MarketState resolve(CTraderOpenApiClient.CTraderSymbol symbol) {
        Instant observedAt = clock.instant();
        String mode = symbol.tradingMode();
        if (mode == null || mode.isBlank()) return unknown(observedAt);
        if (!enabled(mode)) return closed(MarketClosureReason.HALTED, observedAt);
        if (symbol.scheduleTimeZone() == null || symbol.scheduleTimeZone().isBlank()
                || symbol.schedule().stream().noneMatch(this::validInterval)) {
            return unknown(observedAt);
        }

        ZoneId zone;
        try {
            zone = ZoneId.of(symbol.scheduleTimeZone());
        } catch (RuntimeException ignored) {
            return unknown(observedAt);
        }
        ZonedDateTime local = observedAt.atZone(zone);
        if (holiday(symbol, local)) return closed(MarketClosureReason.OUTSIDE_TRADING_HOURS, observedAt);
        long second = weekSecond(local);
        boolean open = symbol.schedule().stream().filter(this::validInterval)
                .anyMatch(interval -> interval.startSecond() <= second && second < interval.endSecond());
        return open ? MarketState.builder().tradingStatus(TradingStatus.OPEN).tradable(true)
                .lastUpdated(observedAt).build()
                : closed(MarketClosureReason.OUTSIDE_TRADING_HOURS, observedAt);
    }

    private boolean holiday(CTraderOpenApiClient.CTraderSymbol symbol, ZonedDateTime local) {
        for (CTraderOpenApiClient.CTraderHoliday holiday : symbol.holidays()) {
            if (holiday.holidayDateEpochMillis() < 0) continue;
            LocalDate holidayDate = LocalDate.ofEpochDay(holiday.holidayDateEpochMillis() / 86_400_000L);
            if ((!holiday.recurring() && !holidayDate.equals(local.toLocalDate()))
                    || (holiday.recurring() && (holidayDate.getMonth() != local.getMonth()
                    || holidayDate.getDayOfMonth() != local.getDayOfMonth()))) continue;
            long second = local.toLocalTime().toSecondOfDay();
            long start = holiday.startSecond() == null ? 0 : holiday.startSecond();
            long end = holiday.endSecond() == null ? 86_400 : holiday.endSecond();
            if (start <= second && second < end) return true;
        }
        return false;
    }

    private long weekSecond(ZonedDateTime local) {
        DayOfWeek day = local.getDayOfWeek();
        long dayOffset = day == DayOfWeek.SUNDAY ? 0 : day.getValue();
        return dayOffset * 86_400L + local.toLocalTime().toSecondOfDay();
    }

    private boolean validInterval(CTraderOpenApiClient.CTraderInterval interval) {
        return interval.startSecond() >= 0 && interval.endSecond() <= SECONDS_PER_WEEK
                && interval.startSecond() < interval.endSecond();
    }

    private boolean enabled(String mode) {
        return "0".equals(mode) || "ENABLED".equalsIgnoreCase(mode);
    }

    private MarketState unknown(Instant observedAt) {
        return MarketState.builder().tradingStatus(TradingStatus.UNKNOWN).tradable(false)
                .closureReason(MarketClosureReason.UNKNOW).lastUpdated(observedAt).build();
    }

    private MarketState closed(MarketClosureReason reason, Instant observedAt) {
        return MarketState.builder().tradingStatus(TradingStatus.CLOSED).tradable(false)
                .closureReason(reason).lastUpdated(observedAt).build();
    }
}
