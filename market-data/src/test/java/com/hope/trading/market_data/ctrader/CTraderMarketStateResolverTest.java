package com.hope.trading.market_data.ctrader;

import com.hope.trading.market_data.helper.MarketClosureReason;
import com.hope.trading.market_data.helper.TradingStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CTraderMarketStateResolverTest {
    @Test
    void opensOnlyInsideTheBrokerPublishedSession() {
        var resolver = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC));
        var state = resolver.resolve(symbol("UTC", List.of(new CTraderOpenApiClient.CTraderInterval(
                86_400 + 9 * 3_600, 86_400 + 17 * 3_600)), List.of()));

        assertThat(state.getTradingStatus()).isEqualTo(TradingStatus.OPEN);
        assertThat(state.isTradable()).isTrue();
    }

    @Test
    void usesSundayAsTheScheduleWeekOriginAndTreatsEndAsExclusive() {
        var openAtStart = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC));
        var closedAtEnd = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-08T00:00:01Z"), ZoneOffset.UTC));
        var schedule = List.of(new CTraderOpenApiClient.CTraderInterval(0, 1));

        assertThat(openAtStart.resolve(symbol("UTC", schedule, List.of())).getTradingStatus())
                .isEqualTo(TradingStatus.OPEN);
        assertThat(closedAtEnd.resolve(symbol("UTC", schedule, List.of())).getTradingStatus())
                .isEqualTo(TradingStatus.CLOSED);
    }

    @Test
    void closesBeforeSessionAndHandlesSessionCrossingMidnight() {
        var before = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-09T08:59:59Z"), ZoneOffset.UTC));
        var after = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-10T00:30:00Z"), ZoneOffset.UTC));
        var schedule = List.of(new CTraderOpenApiClient.CTraderInterval(
                86_400 + 22 * 3_600, 2 * 86_400 + 2 * 3_600));

        assertThat(before.resolve(symbol("UTC", schedule, List.of())).getTradingStatus())
                .isEqualTo(TradingStatus.CLOSED);
        assertThat(after.resolve(symbol("UTC", schedule, List.of())).getTradingStatus())
                .isEqualTo(TradingStatus.OPEN);
    }

    @Test
    void appliesBrokerHolidayBeforeAQuoteCanMakeTheMarketEligible() {
        var resolver = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC));
        var holiday = new CTraderOpenApiClient.CTraderHoliday(
                Instant.parse("2026-11-09T00:00:00Z").toEpochMilli(), false, null, null, "UTC");

        var state = resolver.resolve(symbol("UTC", List.of(new CTraderOpenApiClient.CTraderInterval(
                86_400, 2 * 86_400)), List.of(holiday)));

        assertThat(state.getTradingStatus()).isEqualTo(TradingStatus.CLOSED);
        assertThat(state.getClosureReason()).isEqualTo(MarketClosureReason.OUTSIDE_TRADING_HOURS);
    }

    @Test
    void evaluatesHolidayInTheHolidayPublishedTimezone() {
        var resolver = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-10T00:30:00Z"), ZoneOffset.UTC));
        var holiday = new CTraderOpenApiClient.CTraderHoliday(
                Instant.parse("2026-11-09T00:00:00Z").toEpochMilli(), false, null, null,
                "America/New_York");

        var state = resolver.resolve(symbol("UTC", List.of(new CTraderOpenApiClient.CTraderInterval(
                0, 7 * 86_400L)), List.of(holiday)));

        assertThat(state.getTradingStatus()).isEqualTo(TradingStatus.CLOSED);
        assertThat(state.getClosureReason()).isEqualTo(MarketClosureReason.OUTSIDE_TRADING_HOURS);
    }

    @Test
    void treatsInvalidHolidayTimezoneAsUnknown() {
        var resolver = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC));
        var holiday = new CTraderOpenApiClient.CTraderHoliday(
                Instant.parse("2026-11-09T00:00:00Z").toEpochMilli(), false, null, null,
                "invalid/broker-timezone");

        assertThat(resolver.resolve(symbol("UTC", List.of(new CTraderOpenApiClient.CTraderInterval(
                0, 7 * 86_400L)), List.of(holiday))).getTradingStatus())
                .isEqualTo(TradingStatus.UNKNOWN);
    }

    @Test
    void treatsInvalidHolidayIntervalAsUnknown() {
        var resolver = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC));
        var holiday = new CTraderOpenApiClient.CTraderHoliday(
                Instant.parse("2026-11-09T00:00:00Z").toEpochMilli(), false, 20_000L, 10_000L, "UTC");

        assertThat(resolver.resolve(symbol("UTC", List.of(new CTraderOpenApiClient.CTraderInterval(
                0, 7 * 86_400L)), List.of(holiday))).getTradingStatus())
                .isEqualTo(TradingStatus.UNKNOWN);
    }

    @Test
    void treatsInvalidScheduleTimezoneAsUnknown() {
        var resolver = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC));

        assertThat(resolver.resolve(symbol("invalid/broker-timezone", List.of(
                new CTraderOpenApiClient.CTraderInterval(0, 7 * 86_400L)), List.of()))
                .getTradingStatus()).isEqualTo(TradingStatus.UNKNOWN);
    }

    @Test
    void treatsMalformedScheduleIntervalAsUnknown() {
        var resolver = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC));

        assertThat(resolver.resolve(symbol("UTC", List.of(
                new CTraderOpenApiClient.CTraderInterval(20_000L, 10_000L)), List.of()))
                .getTradingStatus()).isEqualTo(TradingStatus.UNKNOWN);
    }

    @Test
    void treatsScheduleWithAnyMalformedIntervalAsUnknown() {
        var resolver = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC));

        assertThat(resolver.resolve(symbol("UTC", List.of(
                new CTraderOpenApiClient.CTraderInterval(0, 86_399),
                new CTraderOpenApiClient.CTraderInterval(20_000L, 10_000L)), List.of()))
                .getTradingStatus()).isEqualTo(TradingStatus.UNKNOWN);
    }

    @Test
    void treatsMissingScheduleAsUnknownAndDisabledTradingAsHalted() {
        var resolver = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC));

        assertThat(resolver.resolve(symbol("UTC", List.of(), List.of())).getTradingStatus())
                .isEqualTo(TradingStatus.UNKNOWN);
        assertThat(resolver.resolve(new CTraderOpenApiClient.CTraderSymbol(42, "EURUSD", 5,
                1, 2, 1, "CLOSE_ONLY_MODE", "UTC", List.of(
                new CTraderOpenApiClient.CTraderInterval(0, 7 * 86_400L)), List.of()))
                .getTradingStatus()).isEqualTo(TradingStatus.CLOSED);
    }

    @Test
    void derivesWeekendClosureForForexWhenTheProviderScheduleIsMissing() {
        var resolver = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-07T13:01:00Z"), ZoneOffset.UTC));

        var state = resolver.resolve(symbol("UTC", List.of(), List.of()),
                Instant.parse("2026-11-06T21:55:00Z"));

        assertThat(state.getTradingStatus()).isEqualTo(TradingStatus.CLOSED);
        assertThat(state.getClosureReason()).isEqualTo(MarketClosureReason.WEEKEND);
    }

    @Test
    void closesForexOnSaturdayEvenWhenProviderScheduleClaimsItIsOpen() {
        var resolver = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-07T13:01:00Z"), ZoneOffset.UTC));

        var state = resolver.resolve(symbol("UTC", List.of(
                new CTraderOpenApiClient.CTraderInterval(0, 7 * 86_400L)), List.of()));

        assertThat(state.getTradingStatus()).isEqualTo(TradingStatus.CLOSED);
        assertThat(state.getClosureReason()).isEqualTo(MarketClosureReason.WEEKEND);
    }

    @Test
    void doesNotDeriveWeekendClosureForNonForexSymbols() {
        var resolver = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-07T13:01:00Z"), ZoneOffset.UTC));
        var symbol = new CTraderOpenApiClient.CTraderSymbol(42, "BTCUSD", 2, 1, 2, 1,
                "ENABLED", null, List.of(), List.of());

        assertThat(resolver.resolve(symbol, Instant.parse("2026-11-06T21:55:00Z"))
                .getTradingStatus()).isEqualTo(TradingStatus.UNKNOWN);
    }

    @Test
    void doesNotDeriveWeekendClosureWhenTheLastProviderEventIsToday() {
        var resolver = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-07T13:01:00Z"), ZoneOffset.UTC));

        assertThat(resolver.resolve(symbol("UTC", List.of(), List.of()),
                Instant.parse("2026-11-07T12:59:00Z")).getTradingStatus())
                .isEqualTo(TradingStatus.UNKNOWN);
    }

    @Test
    void derivesWeekendClosureFromCanonicalSymbolWhenProviderMetadataIsUnavailable() {
        var resolver = new CTraderMarketStateResolver(Clock.fixed(
                Instant.parse("2026-11-07T13:01:00Z"), ZoneOffset.UTC));

        var state = resolver.resolveWeekendFallback("EUR/USD",
                Instant.parse("2026-11-06T21:55:00Z"));

        assertThat(state.getTradingStatus()).isEqualTo(TradingStatus.CLOSED);
        assertThat(state.getClosureReason()).isEqualTo(MarketClosureReason.WEEKEND);
    }

    private CTraderOpenApiClient.CTraderSymbol symbol(String zone,
                                                       List<CTraderOpenApiClient.CTraderInterval> schedule,
                                                       List<CTraderOpenApiClient.CTraderHoliday> holidays) {
        return new CTraderOpenApiClient.CTraderSymbol(42, "EURUSD", 5, 1, 2, 1,
                "ENABLED", zone, schedule, holidays);
    }
}
