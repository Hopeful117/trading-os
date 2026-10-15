package com.hope.trading.market_intelligence.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

public final class OhlcTemporalCalculator {
    private static final int RETURN_SCALE = 16;

    public OhlcTemporalAnalysis calculate(
            HistoricalOhlcContext history,
            Instant evaluationCutoff
    ) {
        if (history == null || history.candles().isEmpty()) {
            return unavailable(history, DeterministicAnalysisStatus.INSUFFICIENT_DATA);
        }
        List<OhlcPoint> candles = history.candles();
        for (int index = 0; index < candles.size(); index++) {
            OhlcPoint candle = candles.get(index);
            if (!valid(candle) || (evaluationCutoff != null
                    && (candle.openTime().isAfter(evaluationCutoff)
                    || candle.closeTime().isAfter(evaluationCutoff)))) {
                return unavailable(history, DeterministicAnalysisStatus.INVALID_INPUT);
            }
            if (index > 0 && !candles.get(index - 1).closeTime().isBefore(candle.closeTime())) {
                return unavailable(history, DeterministicAnalysisStatus.INVALID_INPUT);
            }
        }

        OhlcPoint first = candles.getFirst();
        OhlcPoint last = candles.getLast();
        BigDecimal highest = candles.stream().map(OhlcPoint::high).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal lowest = candles.stream().map(OhlcPoint::low).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal priceChange = last.close().subtract(first.open());
        BigDecimal simpleReturn = candles.size() < 2
                ? null : priceChange.divide(first.open(), RETURN_SCALE, RoundingMode.HALF_UP);
        DeterministicAnalysisStatus simpleReturnStatus = candles.size() < 2
                ? DeterministicAnalysisStatus.INSUFFICIENT_DATA : DeterministicAnalysisStatus.AVAILABLE;
        return new OhlcTemporalAnalysis(
                history.marketId(), history.interval(), first.openTime(), last.closeTime(), candles.size(),
                highest, lowest, highest.subtract(lowest), last.close(), priceChange, simpleReturn,
                simpleReturnStatus,
                DeterministicAnalysisStatus.AVAILABLE, OhlcTemporalAnalysis.ALGORITHM_VERSION);
    }

    private boolean valid(OhlcPoint candle) {
        return candle != null
                && candle.openTime() != null
                && candle.closeTime() != null
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

    private OhlcTemporalAnalysis unavailable(
            HistoricalOhlcContext history, DeterministicAnalysisStatus status) {
        return new OhlcTemporalAnalysis(
                history == null ? null : history.marketId(),
                history == null ? "UNKNOWN" : history.interval(),
                null, null, history == null ? 0 : history.candles().size(),
                null, null, null, null, null, null, status, status,
                OhlcTemporalAnalysis.ALGORITHM_VERSION);
    }
}
