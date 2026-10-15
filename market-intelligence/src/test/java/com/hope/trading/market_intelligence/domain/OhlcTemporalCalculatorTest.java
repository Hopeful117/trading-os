package com.hope.trading.market_intelligence.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OhlcTemporalCalculatorTest {
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final Instant CUTOFF = Instant.parse("2026-10-15T12:00:00Z");
    private final OhlcTemporalCalculator calculator = new OhlcTemporalCalculator();

    @Test
    void calculatesRangeAndSimpleReturnOverOrderedEvidence() {
        HistoricalOhlcContext history = new HistoricalOhlcContext(
                MARKET_ID, "FIFTEEN_MINUTES", List.of(
                candle(CUTOFF.minusSeconds(1800), "100", "110", "90", "105"),
                candle(CUTOFF.minusSeconds(900), "105", "120", "100", "115")));

        OhlcTemporalAnalysis result = calculator.calculate(history, CUTOFF);

        assertThat(result.status()).isEqualTo(DeterministicAnalysisStatus.AVAILABLE);
        assertThat(result.range()).isEqualByComparingTo("30");
        assertThat(result.closePrice()).isEqualByComparingTo("115");
        assertThat(result.priceChange()).isEqualByComparingTo("15");
        assertThat(result.simpleReturn()).isEqualByComparingTo("0.15");
    }

    @Test
    void rejectsInsufficientFutureAndOutOfOrderEvidence() {
        assertThat(calculator.calculate(new HistoricalOhlcContext(
                MARKET_ID, "15m", List.of(candle(CUTOFF.minusSeconds(900), "100", "101", "99", "100"))), CUTOFF).status())
                .isEqualTo(DeterministicAnalysisStatus.AVAILABLE);
        assertThat(calculator.calculate(new HistoricalOhlcContext(
                MARKET_ID, "15m", List.of(candle(CUTOFF.minusSeconds(900), "100", "101", "99", "100"))), CUTOFF)
                .simpleReturnStatus()).isEqualTo(DeterministicAnalysisStatus.INSUFFICIENT_DATA);

        OhlcPoint future = candle(CUTOFF.plusSeconds(1), "105", "110", "100", "106");
        OhlcPoint past = candle(CUTOFF.minusSeconds(900), "100", "101", "99", "100");
        assertThat(calculator.calculate(new HistoricalOhlcContext(
                MARKET_ID, "15m", List.of(past, future)), CUTOFF).status())
                .isEqualTo(DeterministicAnalysisStatus.INVALID_INPUT);

        assertThat(calculator.calculate(new HistoricalOhlcContext(
                MARKET_ID, "15m", List.of(future, past)), null).status())
                .isEqualTo(DeterministicAnalysisStatus.INVALID_INPUT);
    }

    @Test
    void rejectsInvalidStartPrice() {
        OhlcPoint invalid = new OhlcPoint(
                CUTOFF.minusSeconds(1800), CUTOFF.minusSeconds(900),
                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
        OhlcPoint valid = candle(CUTOFF.minusSeconds(900), "100", "101", "99", "100");

        assertThat(calculator.calculate(new HistoricalOhlcContext(
                MARKET_ID, "15m", List.of(invalid, valid)), CUTOFF).status())
                .isEqualTo(DeterministicAnalysisStatus.INVALID_INPUT);
    }

    @Test
    void rejectsMalformedCandleRelationships() {
        OhlcPoint invalidPriceRelationship = new OhlcPoint(
                CUTOFF.minusSeconds(1800), CUTOFF.minusSeconds(900),
                new BigDecimal("100"), new BigDecimal("105"),
                new BigDecimal("95"), new BigDecimal("110"), BigDecimal.ONE);
        OhlcPoint invalidTimeRelationship = new OhlcPoint(
                CUTOFF.minusSeconds(900), CUTOFF.minusSeconds(1800),
                new BigDecimal("100"), new BigDecimal("105"),
                new BigDecimal("95"), new BigDecimal("100"), BigDecimal.ONE);

        assertThat(calculator.calculate(new HistoricalOhlcContext(
                MARKET_ID, "15m", List.of(invalidPriceRelationship)), CUTOFF).status())
                .isEqualTo(DeterministicAnalysisStatus.INVALID_INPUT);
        assertThat(calculator.calculate(new HistoricalOhlcContext(
                MARKET_ID, "15m", List.of(invalidTimeRelationship)), CUTOFF).status())
                .isEqualTo(DeterministicAnalysisStatus.INVALID_INPUT);
    }

    private OhlcPoint candle(Instant openTime, String open, String high, String low, String close) {
        return new OhlcPoint(openTime, openTime.plus(Duration.ofMinutes(15)),
                new BigDecimal(open), new BigDecimal(high), new BigDecimal(low),
                new BigDecimal(close), BigDecimal.ONE);
    }
}
