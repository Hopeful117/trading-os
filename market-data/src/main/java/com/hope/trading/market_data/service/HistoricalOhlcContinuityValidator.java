package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.HistoricalOhlcContinuityReport;
import com.hope.trading.market_data.model.HistoricalOhlcEvidence;
import com.hope.trading.market_data.model.HistoricalOhlcEvidenceOrigin;
import com.hope.trading.market_data.model.HistoricalOhlcGapAssessment;
import com.hope.trading.market_data.model.HistoricalOhlcGapClassification;
import com.hope.trading.market_data.model.OhlcInterval;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Reports continuity without fabricating observations. Unknown session availability stays explicit.
 */
public final class HistoricalOhlcContinuityValidator {
    private static final int MAX_EXPECTED_OBSERVATIONS = 10_000;
    private static final Set<String> FIAT_CODES = Set.of(
            "AUD", "CAD", "CHF", "CNH", "CZK", "DKK", "EUR", "GBP", "HKD", "HUF",
            "ILS", "JPY", "MXN", "NOK", "NZD", "PLN", "RON", "SEK", "SGD", "THB",
            "TRY", "USD", "ZAR");

    private HistoricalOhlcContinuityValidator() {
    }

    public static HistoricalOhlcContinuityReport assess(
            UUID marketId,
            String canonicalSymbol,
            OhlcInterval interval,
            Instant fromInclusive,
            Instant toExclusive,
            List<HistoricalOhlcEvidence> candidates,
            Function<Instant, HistoricalOhlcGapClassification> availability
    ) {
        if (marketId == null || canonicalSymbol == null || canonicalSymbol.isBlank() || interval == null
                || fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)
                || availability == null) {
            throw new IllegalArgumentException("market, interval, bounds and availability are required");
        }
        List<HistoricalOhlcEvidence> scoped = (candidates == null ? List.<HistoricalOhlcEvidence>of() : candidates)
                .stream()
                .filter(evidence -> evidence != null && marketId.equals(evidence.marketId())
                        && evidence.interval() == interval
                        && !evidence.openTime().isBefore(fromInclusive)
                        && !evidence.closeTime().isAfter(toExclusive))
                .toList();
        Instant anchor = scoped.stream().map(HistoricalOhlcEvidence::openTime).min(Instant::compareTo).orElse(null);
        List<Instant> expected = expectedOpenTimes(interval, fromInclusive, toExclusive, anchor);
        HashSet<Instant> expectedSet = new HashSet<>(expected);
        Map<Instant, List<HistoricalOhlcEvidence>> byOpen = new HashMap<>();
        List<HistoricalOhlcGapAssessment> assessments = new ArrayList<>();
        for (HistoricalOhlcEvidence evidence : scoped) {
            byOpen.computeIfAbsent(evidence.openTime(), ignored -> new ArrayList<>()).add(evidence);
            if (!expectedSet.contains(evidence.openTime())) {
                assessments.add(new HistoricalOhlcGapAssessment(evidence.openTime(), evidence.closeTime(),
                        HistoricalOhlcGapClassification.INVALID_CANDLE,
                        "Candle open time does not match the provider timeframe boundary"));
            }
        }

        int valid = 0;
        Instant latest = null;
        for (Instant open : expected) {
            Instant close = open.plus(interval.getDuration());
            List<HistoricalOhlcEvidence> atOpen = byOpen.getOrDefault(open, List.of());
            if (atOpen.size() > 1) {
                assessments.add(new HistoricalOhlcGapAssessment(open, close,
                        HistoricalOhlcGapClassification.DUPLICATE,
                        "Multiple persisted observations share the candle open time"));
                continue;
            }
            if (atOpen.size() == 1) {
                HistoricalOhlcEvidence evidence = atOpen.getFirst();
                if (!valid(evidence, interval, open, close)) {
                    assessments.add(new HistoricalOhlcGapAssessment(open, close,
                            HistoricalOhlcGapClassification.INVALID_CANDLE,
                            "Persisted observation is not a completed canonical OHLC candle"));
                    continue;
                }
                valid++;
                latest = open;
                continue;
            }
            HistoricalOhlcGapClassification classification = availability.apply(open);
            if (classification == null) classification = HistoricalOhlcGapClassification.UNCERTAIN;
            assessments.add(new HistoricalOhlcGapAssessment(open, close, classification,
                    reason(classification)));
        }
        return new HistoricalOhlcContinuityReport(marketId, canonicalSymbol, interval,
                fromInclusive, toExclusive, expected.size(), valid, latest, assessments);
    }

    public static Function<Instant, HistoricalOhlcGapClassification> cTraderForexWeekendAvailability(
            String canonicalSymbol) {
        if (!isForex(canonicalSymbol)) {
            return ignored -> HistoricalOhlcGapClassification.UNCERTAIN;
        }
        return open -> isObservedWeekendClosure(open)
                ? HistoricalOhlcGapClassification.EXPECTED_MARKET_CLOSURE
                : HistoricalOhlcGapClassification.UNCERTAIN;
    }

    public static Function<Instant, HistoricalOhlcGapClassification> unknownAvailability() {
        return ignored -> HistoricalOhlcGapClassification.UNCERTAIN;
    }

    private static boolean valid(HistoricalOhlcEvidence evidence, OhlcInterval interval,
                                 Instant open, Instant close) {
        return evidence.provider() == MarketProvider.CTRADER
                && evidence.origin() == HistoricalOhlcEvidenceOrigin.CAPTURED
                && evidence.closed() && !evidence.synthetic()
                && evidence.openTime().equals(open) && evidence.closeTime().equals(close)
                && present(evidence.open()) && present(evidence.high())
                && present(evidence.low()) && present(evidence.close())
                && evidence.high().compareTo(evidence.low()) >= 0
                && evidence.open().compareTo(evidence.low()) >= 0
                && evidence.open().compareTo(evidence.high()) <= 0
                && evidence.close().compareTo(evidence.low()) >= 0
                && evidence.close().compareTo(evidence.high()) <= 0;
    }

    private static boolean present(BigDecimal value) {
        return value != null && value.signum() >= 0;
    }

    private static List<Instant> expectedOpenTimes(OhlcInterval interval, Instant from, Instant to, Instant anchor) {
        long seconds = interval.getDuration().getSeconds();
        Instant open = anchor == null
                ? Instant.ofEpochSecond(Math.floorDiv(from.getEpochSecond(), seconds) * seconds)
                : anchor;
        while (open.isBefore(from)) open = open.plusSeconds(seconds);
        List<Instant> expected = new ArrayList<>();
        while (!open.plusSeconds(seconds).isAfter(to)) {
            if (expected.size() == MAX_EXPECTED_OBSERVATIONS) {
                throw new IllegalArgumentException("Continuity range exceeds " + MAX_EXPECTED_OBSERVATIONS);
            }
            expected.add(open);
            open = open.plusSeconds(seconds);
        }
        return expected;
    }

    private static boolean isObservedWeekendClosure(Instant open) {
        ZonedDateTime utc = open.atZone(ZoneOffset.UTC);
        DayOfWeek day = utc.getDayOfWeek();
        return (day == DayOfWeek.FRIDAY && utc.getHour() >= 21)
                || day == DayOfWeek.SATURDAY
                || (day == DayOfWeek.SUNDAY && utc.getHour() < 21);
    }

    private static boolean isForex(String symbol) {
        String normalized = symbol == null ? "" : symbol.replace("/", "").toUpperCase();
        return normalized.matches("[A-Z]{6}")
                && FIAT_CODES.contains(normalized.substring(0, 3))
                && FIAT_CODES.contains(normalized.substring(3));
    }

    private static String reason(HistoricalOhlcGapClassification classification) {
        return switch (classification) {
            case EXPECTED_MARKET_CLOSURE -> "cTrader FX weekend closure pattern";
            case MISSING_DATA -> "Provider/session evidence indicates a candle should exist";
            case INVALID_CANDLE -> "Invalid OHLC evidence";
            case DUPLICATE -> "Duplicate candle timestamp";
            case UNCERTAIN -> "Historical session availability is not authoritative for this interval";
        };
    }
}
