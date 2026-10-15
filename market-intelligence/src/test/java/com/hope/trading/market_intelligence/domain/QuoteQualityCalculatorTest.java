package com.hope.trading.market_intelligence.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteQualityCalculatorTest {
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final Instant EVIDENCE_AT = Instant.parse("2026-10-15T12:00:00Z");
    private final QuoteQualityCalculator calculator = new QuoteQualityCalculator();

    @Test
    void calculatesTypedSpreadMeasurementsWithBasisPoints() {
        QuoteQualityAnalysis result = calculator.calculate(
                MARKET_ID, new BigDecimal("99"), new BigDecimal("101"), EVIDENCE_AT);

        assertThat(result.status()).isEqualTo(DeterministicAnalysisStatus.AVAILABLE);
        assertThat(result.midpoint()).isEqualByComparingTo("100");
        assertThat(result.absoluteSpread()).isEqualByComparingTo("2");
        assertThat(result.relativeSpread()).isEqualByComparingTo("0.02");
        assertThat(result.spreadBps()).isEqualByComparingTo("200.00000000");
        assertThat(result.algorithmVersion()).isEqualTo(QuoteQualityAnalysis.ALGORITHM_VERSION);
    }

    @Test
    void acceptsZeroSpread() {
        QuoteQualityAnalysis result = calculator.calculate(
                MARKET_ID, new BigDecimal("100"), new BigDecimal("100"), EVIDENCE_AT);

        assertThat(result.status()).isEqualTo(DeterministicAnalysisStatus.AVAILABLE);
        assertThat(result.absoluteSpread()).isEqualByComparingTo("0");
        assertThat(result.spreadBps()).isEqualByComparingTo("0");
    }

    @Test
    void classifiesMissingAndInvalidQuotesExplicitly() {
        assertThat(calculator.calculate(MARKET_ID, null, BigDecimal.ONE, EVIDENCE_AT).status())
                .isEqualTo(DeterministicAnalysisStatus.INSUFFICIENT_DATA);
        assertThat(calculator.calculate(MARKET_ID, BigDecimal.ZERO, BigDecimal.ONE, EVIDENCE_AT).status())
                .isEqualTo(DeterministicAnalysisStatus.INVALID_INPUT);
        assertThat(calculator.calculate(MARKET_ID, new BigDecimal("101"), new BigDecimal("99"), EVIDENCE_AT).status())
                .isEqualTo(DeterministicAnalysisStatus.INVALID_INPUT);
    }

    @Test
    void sameInputProducesSameResult() {
        QuoteQualityAnalysis first = calculator.calculate(
                MARKET_ID, new BigDecimal("99"), new BigDecimal("101"), EVIDENCE_AT);
        QuoteQualityAnalysis second = calculator.calculate(
                MARKET_ID, new BigDecimal("99"), new BigDecimal("101"), EVIDENCE_AT);

        assertThat(second).isEqualTo(first);
    }
}
