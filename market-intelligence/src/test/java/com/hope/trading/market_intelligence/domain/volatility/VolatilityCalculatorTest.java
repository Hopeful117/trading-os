package com.hope.trading.market_intelligence.domain.volatility;

import com.hope.trading.market_intelligence.domain.HistoricalOhlcContext;
import com.hope.trading.market_intelligence.domain.OhlcPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class VolatilityCalculatorTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private final VolatilityCalculator calculator = new VolatilityCalculator();

    @Test
    void computesDeterministicVolatilityAndEfficiencyFromCompletedCandles() {
        HistoricalOhlcContext history = history(IntStream.range(0, 40)
                .mapToObj(i -> candle(i, 100 + i, 99 + i, 100 + i))
                .toList());

        VolatilityAssessment result = calculator.calculate(history, at(39), "input-v1");

        assertThat(result.status()).isEqualTo(VolatilityAssessment.Status.AVAILABLE);
        assertThat(result.atr()).isEqualByComparingTo("2");
        assertThat(result.normalizedAtr()).isPositive();
        assertThat(result.realizedVolatility()).isPositive();
        assertThat(result.compressionRatio()).isEqualByComparingTo("1");
        assertThat(result.directionalEfficiency()).isEqualByComparingTo("1");
        assertThat(result.state()).isEqualTo(VolatilityAssessment.State.NORMAL);
        assertThat(result.resultFingerprint()).hasSize(64);
    }

    @Test
    void cutoffExcludesFutureCandlesAndRequiresTheFullBaseline() {
        HistoricalOhlcContext history = history(IntStream.range(0, 40)
                .mapToObj(i -> candle(i, 100 + i, 99 + i, 100 + i))
                .toList());

        VolatilityAssessment result = calculator.calculate(history, at(30), "input-v1");

        assertThat(result.status()).isEqualTo(VolatilityAssessment.Status.INSUFFICIENT_DATA);
        assertThat(result.cutoff()).isEqualTo(at(30));
    }

    @Test
    void flatPricesProduceZeroEfficiencyWithoutDividingByZero() {
        HistoricalOhlcContext history = history(IntStream.range(0, 40)
                .mapToObj(i -> candle(i, 100, 100, 100))
                .toList());

        VolatilityAssessment result = calculator.calculate(history, at(39), "flat");

        assertThat(result.status()).isEqualTo(VolatilityAssessment.Status.AVAILABLE);
        assertThat(result.realizedVolatility()).isZero();
        assertThat(result.directionalEfficiency()).isZero();
        assertThat(result.state()).isEqualTo(VolatilityAssessment.State.NORMAL);
    }

    @Test
    void invalidCandleIsReportedWithoutProducingFeatures() {
        HistoricalOhlcContext history = history(List.of(new OhlcPoint(
                at(0), at(1), new BigDecimal("100"), new BigDecimal("99"),
                new BigDecimal("101"), new BigDecimal("100"), BigDecimal.ONE)));

        VolatilityAssessment result = calculator.calculate(history, at(1), "invalid");

        assertThat(result.status()).isEqualTo(VolatilityAssessment.Status.INVALID_INPUT);
        assertThat(result.atr()).isNull();
        assertThat(result.reason()).contains("invalid");
    }

    @Test
    void refusesVolatilityFeaturesAcrossTemporalGaps() {
        HistoricalOhlcContext history = history(IntStream.range(0, 40)
                .mapToObj(i -> candle(i < 20 ? i : i + 1, 100 + i, 99 + i, 100 + i))
                .toList());

        VolatilityAssessment result = calculator.calculate(history, at(41), "gap");

        assertThat(result.status()).isEqualTo(VolatilityAssessment.Status.INSUFFICIENT_DATA);
        assertThat(result.atr()).isNull();
        assertThat(result.reason()).contains("temporal gap");
    }

    private HistoricalOhlcContext history(List<OhlcPoint> candles) {
        return new HistoricalOhlcContext(
                java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"), "1h", candles);
    }

    private OhlcPoint candle(int index, int open, int low, int close) {
        return new OhlcPoint(at(index), at(index + 1), BigDecimal.valueOf(open),
                BigDecimal.valueOf(close + 1L), BigDecimal.valueOf(low), BigDecimal.valueOf(close), BigDecimal.ONE);
    }

    private Instant at(int index) {
        return START.plusSeconds(index * 3600L);
    }
}
