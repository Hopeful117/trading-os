package com.hope.trading.market_intelligence.domain.trendcontext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class TrendContextRoleSeries {
    private final TrendContextRole role;
    private final String interval;
    private final List<TrendContextCandle> candles;
    private final List<TrendContextCandle> calculationReadyCandles;
    private final List<String> exclusionFindings;
    private final List<TrendContextGapFinding> gapFindings;
    private final TrendContextSourceReference sourceReference;
    private final TrendContextFreshness freshness;

    private TrendContextRoleSeries(
            TrendContextRole role,
            String interval,
            List<TrendContextCandle> candles,
            List<String> exclusionFindings,
            List<TrendContextGapFinding> gapFindings,
            TrendContextSourceReference sourceReference,
            TrendContextFreshness freshness,
            Instant cutOffAt
    ) {
        this.role = Objects.requireNonNull(role, "role is required");
        this.interval = Objects.requireNonNull(interval, "interval is required");
        this.candles = sorted(candles);
        this.exclusionFindings = List.copyOf(exclusionFindings);
        this.gapFindings = List.copyOf(gapFindings);
        this.sourceReference = Objects.requireNonNull(sourceReference, "sourceReference is required");
        this.freshness = Objects.requireNonNull(freshness, "freshness is required");
        this.calculationReadyCandles = this.candles.stream()
                .filter(candle -> candle.closed()
                        && !candle.synthetic()
                        && !candle.closeTime().isAfter(cutOffAt))
                .toList();
    }

    public static TrendContextRoleSeries of(
            TrendContextRole role,
            String interval,
            List<TrendContextCandle> candles,
            List<String> exclusionFindings,
            List<TrendContextGapFinding> gapFindings,
            TrendContextSourceReference sourceReference,
            TrendContextFreshness freshness,
            Instant cutOffAt
    ) {
        Objects.requireNonNull(cutOffAt, "cutOffAt is required");
        return new TrendContextRoleSeries(
                role, interval, candles, exclusionFindings, gapFindings,
                sourceReference, freshness, cutOffAt);
    }

    public TrendContextRole role() { return role; }
    public String interval() { return interval; }
    public List<TrendContextCandle> candles() { return candles; }
    public List<TrendContextCandle> calculationReadyCandles() { return calculationReadyCandles; }
    public List<String> exclusionFindings() { return exclusionFindings; }
    public List<TrendContextGapFinding> gapFindings() { return gapFindings; }
    public TrendContextSourceReference sourceReference() { return sourceReference; }
    public TrendContextFreshness freshness() { return freshness; }
    public int totalNormalizedCandleCount() { return candles.size(); }
    public int calculationReadyCandleCount() { return calculationReadyCandles.size(); }
    public int excludedCandleCount() {
        return totalNormalizedCandleCount() - calculationReadyCandleCount();
    }

    private static List<TrendContextCandle> sorted(List<TrendContextCandle> values) {
        Objects.requireNonNull(values, "candles is required");
        List<TrendContextCandle> copy = new ArrayList<>(values);
        copy.sort(Comparator.comparing(TrendContextCandle::openTime)
                .thenComparing(TrendContextCandle::closeTime)
                .thenComparing(TrendContextCandle::sourceId));
        return List.copyOf(copy);
    }
}
