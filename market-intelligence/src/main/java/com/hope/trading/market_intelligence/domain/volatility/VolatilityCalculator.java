package com.hope.trading.market_intelligence.domain.volatility;

import com.hope.trading.market_intelligence.domain.HistoricalOhlcContext;
import com.hope.trading.market_intelligence.domain.OhlcPoint;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VolatilityCalculator {
    private static final MathContext MC = MathContext.DECIMAL128;
    public static final int ATR_PERIOD = 14;
    public static final int BASELINE_LENGTH = 20;
    private static final BigDecimal COMPRESSION_THRESHOLD = new BigDecimal("0.80");
    private static final BigDecimal EXPANSION_THRESHOLD = new BigDecimal("1.20");

    public VolatilityAssessment calculate(HistoricalOhlcContext history, Instant requestedCutoff,
                                         String inputFingerprint) {
        Instant cutoff = requestedCutoff != null ? requestedCutoff : latestClose(history);
        String source = inputFingerprint == null ? "unknown" : inputFingerprint;
        if (history == null || history.candles() == null || history.candles().isEmpty()) {
            return unavailable(null, "UNKNOWN", cutoff, VolatilityAssessment.Status.INSUFFICIENT_DATA, source,
                    "Historical OHLC is unavailable");
        }
        List<OhlcPoint> eligible = new ArrayList<>();
        for (OhlcPoint candle : history.candles()) {
            if (!valid(candle)) {
                return unavailable(history.marketId(), history.interval(), cutoff, VolatilityAssessment.Status.INVALID_INPUT, source,
                        "Historical OHLC contains invalid data");
            }
            if (!candle.closeTime().isAfter(cutoff)) eligible.add(candle);
        }
        for (int i = 1; i < eligible.size(); i++) {
            if (!eligible.get(i - 1).closeTime().isBefore(eligible.get(i).closeTime())) {
                return unavailable(history.marketId(), history.interval(), cutoff, VolatilityAssessment.Status.INVALID_INPUT, source,
                        "Historical OHLC candles are not ordered");
            }
            Duration expectedDuration = Duration.between(eligible.get(0).openTime(), eligible.get(0).closeTime());
            Duration currentDuration = Duration.between(eligible.get(i).openTime(), eligible.get(i).closeTime());
            if (!expectedDuration.isPositive() || !expectedDuration.equals(currentDuration)) {
                return unavailable(history.marketId(), history.interval(), cutoff,
                        VolatilityAssessment.Status.INVALID_INPUT, source,
                        "Historical OHLC candles have inconsistent durations");
            }
            if (!eligible.get(i - 1).closeTime().equals(eligible.get(i).openTime())) {
                return unavailable(history.marketId(), history.interval(), cutoff,
                        VolatilityAssessment.Status.INSUFFICIENT_DATA, source,
                        "Historical OHLC contains a temporal gap");
            }
        }
        int required = ATR_PERIOD + BASELINE_LENGTH - 1;
        if (eligible.size() < required) {
            return unavailable(history.marketId(), history.interval(), cutoff, VolatilityAssessment.Status.INSUFFICIENT_DATA, source,
                    "Historical OHLC does not contain the volatility lookback");
        }

        List<BigDecimal> trueRanges = trueRanges(eligible);
        List<BigDecimal> atrs = new ArrayList<>();
        for (int end = ATR_PERIOD; end <= trueRanges.size(); end++) {
            atrs.add(mean(trueRanges.subList(end - ATR_PERIOD, end)));
        }
        BigDecimal atr = atrs.getLast();
        BigDecimal baseline = mean(atrs.subList(atrs.size() - BASELINE_LENGTH, atrs.size()));
        BigDecimal close = eligible.getLast().close();
        BigDecimal normalizedAtr = atr.divide(close, MC);
        BigDecimal compressionRatio = baseline.signum() == 0 ? BigDecimal.ZERO : atr.divide(baseline, MC);
        BigDecimal realizedVolatility = realizedVolatility(eligible.subList(eligible.size() - ATR_PERIOD, eligible.size()));
        BigDecimal efficiency = directionalEfficiency(eligible.subList(eligible.size() - ATR_PERIOD, eligible.size()));
        VolatilityAssessment.State state = baseline.signum() == 0 ? VolatilityAssessment.State.INSUFFICIENT_EVIDENCE
                : compressionRatio.compareTo(COMPRESSION_THRESHOLD) < 0 ? VolatilityAssessment.State.COMPRESSION
                : compressionRatio.compareTo(EXPANSION_THRESHOLD) > 0 ? VolatilityAssessment.State.EXPANSION : VolatilityAssessment.State.NORMAL;
        String fingerprint = ArtifactFingerprint.ofInputs(List.of(
                VolatilityAssessment.ALGORITHM_VERSION, source, history.marketId().toString(), history.interval(), cutoff.toString(),
                Integer.toString(ATR_PERIOD), Integer.toString(BASELINE_LENGTH), atr.toPlainString(),
                normalizedAtr.toPlainString(), realizedVolatility.toPlainString(), compressionRatio.toPlainString(),
                efficiency.toPlainString(), state.name())).value();
        return new VolatilityAssessment(history.marketId(), history.interval(), cutoff, VolatilityAssessment.Status.AVAILABLE,
                ATR_PERIOD, BASELINE_LENGTH, atr, normalizedAtr, realizedVolatility, compressionRatio,
                efficiency, state, VolatilityAssessment.ALGORITHM_VERSION, source, fingerprint,
                "Deterministic OHLC volatility measurements are available");
    }

    private List<BigDecimal> trueRanges(List<OhlcPoint> candles) {
        List<BigDecimal> result = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            OhlcPoint candle = candles.get(i);
            BigDecimal range = candle.high().subtract(candle.low(), MC);
            if (i > 0) {
                BigDecimal previousClose = candles.get(i - 1).close();
                range = range.max(candle.high().subtract(previousClose, MC).abs())
                        .max(candle.low().subtract(previousClose, MC).abs());
            }
            result.add(range);
        }
        return result;
    }

    private BigDecimal realizedVolatility(List<OhlcPoint> candles) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 1; i < candles.size(); i++) {
            BigDecimal change = candles.get(i).close().subtract(candles.get(i - 1).close(), MC)
                    .divide(candles.get(i - 1).close(), MC);
            sum = sum.add(change.multiply(change, MC), MC);
        }
        return sum.divide(BigDecimal.valueOf(candles.size() - 1L), MC).sqrt(MC);
    }

    private BigDecimal directionalEfficiency(List<OhlcPoint> candles) {
        BigDecimal path = BigDecimal.ZERO;
        for (int i = 1; i < candles.size(); i++) {
            path = path.add(candles.get(i).close().subtract(candles.get(i - 1).close(), MC).abs(), MC);
        }
        if (path.signum() == 0) return BigDecimal.ZERO;
        return candles.getLast().close().subtract(candles.getFirst().close(), MC).abs().divide(path, MC);
    }

    private BigDecimal mean(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, (left, right) -> left.add(right, MC))
                .divide(BigDecimal.valueOf(values.size()), MC);
    }

    private VolatilityAssessment unavailable(UUID marketId, String interval, Instant cutoff,
                                             VolatilityAssessment.Status status,
                                             String source, String reason) {
        String fingerprint = ArtifactFingerprint.ofInputs(List.of(VolatilityAssessment.ALGORITHM_VERSION, source, String.valueOf(marketId),
                interval, String.valueOf(cutoff), status.name(), reason)).value();
        return new VolatilityAssessment(marketId, interval, cutoff, status, ATR_PERIOD, BASELINE_LENGTH,
                null, null, null, null, null, VolatilityAssessment.State.INSUFFICIENT_EVIDENCE,
                VolatilityAssessment.ALGORITHM_VERSION, source, fingerprint, reason);
    }

    private Instant latestClose(HistoricalOhlcContext history) {
        return history == null || history.candles() == null || history.candles().isEmpty()
                ? Instant.EPOCH : history.candles().getLast().closeTime();
    }

    private boolean valid(OhlcPoint candle) {
        return candle != null && candle.openTime() != null && candle.closeTime() != null
                && !candle.openTime().isAfter(candle.closeTime()) && candle.open() != null && candle.open().signum() > 0
                && candle.high() != null && candle.high().signum() > 0 && candle.low() != null && candle.low().signum() > 0
                && candle.close() != null && candle.close().signum() > 0 && candle.high().compareTo(candle.low()) >= 0
                && candle.high().compareTo(candle.open()) >= 0 && candle.high().compareTo(candle.close()) >= 0
                && candle.low().compareTo(candle.open()) <= 0 && candle.low().compareTo(candle.close()) <= 0;
    }
}
