package com.hope.trading.market_intelligence.domain.momentum;

import com.hope.trading.market_intelligence.domain.HistoricalOhlcContext;
import com.hope.trading.market_intelligence.domain.OhlcPoint;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MomentumCalculator {
    private static final MathContext MC = MathContext.DECIMAL128;

    public MomentumAssessment calculate(
            HistoricalOhlcContext history, Instant cutoff, String inputFingerprint,
            MomentumPolicy policy) {
        String sourceFingerprint = inputFingerprint == null ? "unknown" : inputFingerprint;
        Instant effectiveCutoff = cutoff != null ? cutoff : latestClose(history);
        if (history == null || history.candles() == null || history.candles().isEmpty()) {
            return unavailable(null, "UNKNOWN", effectiveCutoff, MomentumAssessment.Status.INSUFFICIENT_DATA,
                    sourceFingerprint, "Historical OHLC is unavailable", policy);
        }
        List<OhlcPoint> candles = history.candles();
        List<OhlcPoint> eligible = new ArrayList<>();
        for (OhlcPoint candle : candles) {
            if (!valid(candle)) {
                return unavailable(history.marketId(), history.interval(), effectiveCutoff,
                        MomentumAssessment.Status.INVALID_INPUT, sourceFingerprint,
                        "Historical OHLC contains invalid data", policy);
            }
            if (effectiveCutoff == null || !candle.closeTime().isAfter(effectiveCutoff)) {
                eligible.add(candle);
            }
        }
        for (int index = 1; index < eligible.size(); index++) {
            if (!eligible.get(index - 1).closeTime().isBefore(eligible.get(index).closeTime())) {
                return unavailable(history.marketId(), history.interval(), effectiveCutoff,
                        MomentumAssessment.Status.INVALID_INPUT, sourceFingerprint,
                        "Historical OHLC candles are not ordered", policy);
            }
        }
        if (eligible.size() < policy.lookbackBars()) {
            return unavailable(history.marketId(), history.interval(), effectiveCutoff,
                    MomentumAssessment.Status.INSUFFICIENT_DATA, sourceFingerprint,
                    "Historical OHLC does not contain the Momentum lookback", policy);
        }

        List<OhlcPoint> window = eligible.subList(eligible.size() - policy.lookbackBars(), eligible.size());
        int split = policy.comparisonWindowBars();
        List<OhlcPoint> preceding = window.subList(0, split);
        List<OhlcPoint> recent = window.subList(window.size() - split, window.size());
        BigDecimal first = window.getFirst().close();
        BigDecimal last = window.getLast().close();
        BigDecimal displacement = last.subtract(first, MC);
        MomentumAssessment.Direction direction = displacement.signum() > 0
                ? MomentumAssessment.Direction.UP
                : displacement.signum() < 0
                ? MomentumAssessment.Direction.DOWN : MomentumAssessment.Direction.NEUTRAL;
        BigDecimal normalizedSlope = displacement
                .divide(first, MC)
                .divide(BigDecimal.valueOf(window.size()), MC);
        BigDecimal persistence = persistence(window, direction);
        BigDecimal precedingMovement = movement(preceding);
        BigDecimal recentMovement = movement(recent);
        MomentumAssessment.Acceleration acceleration = recentMovement.compareTo(precedingMovement) > 0
                ? MomentumAssessment.Acceleration.ACCELERATING
                : recentMovement.compareTo(precedingMovement) < 0
                ? MomentumAssessment.Acceleration.DECELERATING
                : MomentumAssessment.Acceleration.STEADY;
        String resultFingerprint = ArtifactFingerprint.ofInputs(List.of(
                MomentumAssessment.ALGORITHM_VERSION, policy.version(), sourceFingerprint,
                history.marketId().toString(), history.interval(), effectiveCutoff.toString(),
                direction.name(), normalizedSlope.toPlainString(), persistence.toPlainString(),
                precedingMovement.toPlainString(), recentMovement.toPlainString(), acceleration.name()
        )).value();
        return new MomentumAssessment(history.marketId(), history.interval(), window.getFirst().openTime(),
                window.getLast().closeTime(), effectiveCutoff, MomentumAssessment.Status.AVAILABLE,
                direction, normalizedSlope, persistence, precedingMovement, recentMovement,
                acceleration, policy.version(), MomentumAssessment.ALGORITHM_VERSION,
                sourceFingerprint, resultFingerprint, "OHLC Momentum is available");
    }

    private BigDecimal persistence(List<OhlcPoint> candles, MomentumAssessment.Direction direction) {
        if (direction == MomentumAssessment.Direction.NEUTRAL || candles.size() < 2) {
            return BigDecimal.ZERO;
        }
        int aligned = 0;
        int transitions = candles.size() - 1;
        for (int index = 1; index < candles.size(); index++) {
            int sign = candles.get(index).close().compareTo(candles.get(index - 1).close());
            if ((direction == MomentumAssessment.Direction.UP && sign > 0)
                    || (direction == MomentumAssessment.Direction.DOWN && sign < 0)) {
                aligned++;
            }
        }
        return BigDecimal.valueOf(aligned)
                .divide(BigDecimal.valueOf(transitions), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal movement(List<OhlcPoint> candles) {
        BigDecimal displacement = candles.getLast().close().subtract(candles.getFirst().close(), MC).abs();
        BigDecimal range = BigDecimal.ZERO;
        for (int index = 0; index < candles.size(); index++) {
            OhlcPoint candle = candles.get(index);
            BigDecimal trueRange = candle.high().subtract(candle.low(), MC);
            if (index > 0) {
                BigDecimal previousClose = candles.get(index - 1).close();
                trueRange = trueRange.max(candle.high().subtract(previousClose, MC).abs())
                        .max(candle.low().subtract(previousClose, MC).abs());
            }
            range = range.add(trueRange, MC);
        }
        return range.signum() == 0 ? BigDecimal.ZERO : displacement.divide(range, MC);
    }

    private MomentumAssessment unavailable(
            java.util.UUID marketId, String interval, Instant cutoff,
            MomentumAssessment.Status status, String inputFingerprint, String reason,
            MomentumPolicy policy) {
        String resultFingerprint = ArtifactFingerprint.ofInputs(List.of(
                MomentumAssessment.ALGORITHM_VERSION, policy.version(), inputFingerprint,
                String.valueOf(marketId), interval, String.valueOf(cutoff), status.name(), reason
        )).value();
        return MomentumAssessment.unavailable(marketId, interval, cutoff, status,
                policy.version(), inputFingerprint, reason, resultFingerprint);
    }

    private Instant latestClose(HistoricalOhlcContext history) {
        if (history == null || history.candles() == null || history.candles().isEmpty()) return Instant.EPOCH;
        return history.candles().getLast().closeTime();
    }

    private boolean valid(OhlcPoint candle) {
        return candle != null
                && candle.openTime() != null && candle.closeTime() != null
                && !candle.openTime().isAfter(candle.closeTime())
                && candle.open() != null && candle.open().signum() > 0
                && candle.high() != null && candle.high().signum() > 0
                && candle.low() != null && candle.low().signum() > 0
                && candle.close() != null && candle.close().signum() > 0
                && candle.high().compareTo(candle.low()) >= 0
                && candle.high().compareTo(candle.open()) >= 0
                && candle.high().compareTo(candle.close()) >= 0
                && candle.low().compareTo(candle.open()) <= 0
                && candle.low().compareTo(candle.close()) <= 0;
    }
}
