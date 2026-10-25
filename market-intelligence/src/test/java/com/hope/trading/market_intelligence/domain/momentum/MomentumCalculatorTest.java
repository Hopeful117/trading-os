package com.hope.trading.market_intelligence.domain.momentum;

import com.hope.trading.market_intelligence.domain.HistoricalOhlcContext;
import com.hope.trading.market_intelligence.domain.OhlcPoint;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MomentumCalculatorTest {
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final Instant CUTOFF = Instant.parse("2026-10-15T12:00:00Z");
    private final MomentumCalculator calculator = new MomentumCalculator();

    @Test
    void calculatesDirectionalMomentumAndAccelerationFromClosedOhlc() {
        HistoricalOhlcContext history = history(List.of(100, 101, 102, 103, 105, 107, 109, 112));

        MomentumAssessment result = calculator.calculate(
                history, CUTOFF, ArtifactFingerprint.empty().value(), MomentumPolicy.V1);

        assertThat(result.status()).isEqualTo(MomentumAssessment.Status.AVAILABLE);
        assertThat(result.direction()).isEqualTo(MomentumAssessment.Direction.UP);
        assertThat(result.directionalPersistence()).isEqualByComparingTo("1.00000000");
        assertThat(result.acceleration()).isEqualTo(MomentumAssessment.Acceleration.ACCELERATING);
        assertThat(result.resultFingerprint()).hasSize(64);
    }

    @Test
    void rejectsFutureAndInsufficientEvidenceWithoutLookAhead() {
        assertThat(calculator.calculate(history(List.of(100, 101, 102)), CUTOFF,
                ArtifactFingerprint.empty().value(), MomentumPolicy.V1).status())
                .isEqualTo(MomentumAssessment.Status.INSUFFICIENT_DATA);

        HistoricalOhlcContext future = new HistoricalOhlcContext(MARKET_ID, "15m", List.of(
                candle(CUTOFF.minusSeconds(7200), 100), candle(CUTOFF.minusSeconds(6300), 101),
                candle(CUTOFF.minusSeconds(5400), 102), candle(CUTOFF.minusSeconds(4500), 103),
                candle(CUTOFF.minusSeconds(3600), 104), candle(CUTOFF.minusSeconds(2700), 105),
                candle(CUTOFF.minusSeconds(1800), 106), candle(CUTOFF.minusSeconds(900), 107),
                candle(CUTOFF.plusSeconds(1), 108)));
        assertThat(calculator.calculate(future, CUTOFF, ArtifactFingerprint.empty().value(), MomentumPolicy.V1).status())
                .isEqualTo(MomentumAssessment.Status.AVAILABLE);
    }

    @Test
    void replayProducesTheSameFingerprint() {
        HistoricalOhlcContext history = history(List.of(100, 101, 102, 103, 104, 105, 106, 107));
        String input = ArtifactFingerprint.ofInputs(List.of("ohlc-input")).value();

        MomentumAssessment first = calculator.calculate(history, CUTOFF, input, MomentumPolicy.V1);
        MomentumAssessment second = calculator.calculate(history, CUTOFF, input, MomentumPolicy.V1);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void detectsBearishDirectionAndDeceleration() {
        MomentumAssessment result = calculator.calculate(
                history(List.of(120, 115, 110, 105, 104, 103, 102, 101)),
                CUTOFF, ArtifactFingerprint.empty().value(), MomentumPolicy.V1);

        assertThat(result.direction()).isEqualTo(MomentumAssessment.Direction.DOWN);
        assertThat(result.acceleration()).isEqualTo(MomentumAssessment.Acceleration.DECELERATING);
        assertThat(result.normalizedSlope()).isNegative();
    }

    @Test
    void inputFingerprintChangesResultFingerprint() {
        HistoricalOhlcContext history = history(List.of(100, 101, 102, 103, 104, 105, 106, 107));

        MomentumAssessment first = calculator.calculate(history, CUTOFF,
                ArtifactFingerprint.ofInputs(List.of("one")).value(), MomentumPolicy.V1);
        MomentumAssessment second = calculator.calculate(history, CUTOFF,
                ArtifactFingerprint.ofInputs(List.of("two")).value(), MomentumPolicy.V1);

        assertThat(second.resultFingerprint()).isNotEqualTo(first.resultFingerprint());
    }

    private HistoricalOhlcContext history(List<Integer> closes) {
        List<OhlcPoint> candles = java.util.stream.IntStream.range(0, closes.size())
                .mapToObj(index -> candle(CUTOFF.minusSeconds((long) (closes.size() - index) * 900), closes.get(index)))
                .toList();
        return new HistoricalOhlcContext(MARKET_ID, "15m", candles);
    }

    private OhlcPoint candle(Instant openTime, int close) {
        BigDecimal value = BigDecimal.valueOf(close);
        return new OhlcPoint(openTime, openTime.plusSeconds(899), value,
                value.add(BigDecimal.ONE), value.subtract(BigDecimal.ONE), value, BigDecimal.ONE);
    }
}
