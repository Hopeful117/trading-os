package com.hope.trading.market_intelligence.domain.outcome;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketOutcomeCalculatorTest {
    private static final Instant T0 = Instant.parse("2026-10-15T10:00:00Z");

    @Test
    void calculatesGoldenFixtureWithExactDecimalSemantics() {
        MarketOutcomeCalculator.CalculatedOutcome result = new MarketOutcomeCalculator().calculate(
                new OutcomeReferenceEvidence(new BigDecimal("100"), T0, T0,
                        "reference-fingerprint", "historical-ohlc-v1"),
                List.of(
                        observation("10:00:00Z", "10:15:00Z", "103", "99", "102", "future-1"),
                        observation("10:15:00Z", "10:30:00Z", "105", "101", "104", "future-2"),
                        observation("10:30:00Z", "10:45:00Z", "104", "99", "100", "future-3"),
                        observation("10:45:00Z", "11:00:00Z", "102", "98", "102", "future-4")
                ),
                4, 4, List.of(), "range-fingerprint", T0, OutcomeHorizon.oneHour());

        assertThat(result.referencePrice()).isEqualByComparingTo("100");
        assertThat(result.finalPrice()).isEqualByComparingTo("102");
        assertThat(result.futureHigh()).isEqualByComparingTo("105");
        assertThat(result.futureLow()).isEqualByComparingTo("98");
        assertThat(result.forwardReturn()).isEqualByComparingTo("0.02");
        assertThat(result.futureHighReturn()).isEqualByComparingTo("0.05");
        assertThat(result.futureLowReturn()).isEqualByComparingTo("-0.02");
        assertThat(result.futureRange()).isEqualByComparingTo("0.07");
    }

    @Test
    void rejectsIncompleteFutureEvidenceInsteadOfUsingZeroValues() {
        var calculator = new MarketOutcomeCalculator();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> calculator.calculate(
                        new OutcomeReferenceEvidence(new BigDecimal("100"), T0, T0,
                                "reference-fingerprint", "historical-ohlc-v1"),
                        List.of(observation("10:00:00Z", "10:15:00Z", "103", "99", "102", "future-1")),
                        4, 1, List.of("10:15:00Z..10:30:00Z"), "range-fingerprint", T0,
                        OutcomeHorizon.oneHour()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("complete future evidence");
    }

    @Test
    void outcomeFingerprintIsStableForIdenticalSemanticInputs() {
        var marketId = java.util.UUID.fromString("11111111-1111-1111-1111-111111111111");
        String first = MarketOutcomeFingerprint.fingerprint(
                MarketOutcomeCalculator.DEFINITION_VERSION, marketId, T0,
                OutcomeHorizon.oneHour(), "reference", "future",
                 ObservedMarketOutcomeStatus.AVAILABLE, new BigDecimal("100"), new BigDecimal("102"),
                 new BigDecimal("105"), new BigDecimal("98"), new BigDecimal("0.02"),
                 new BigDecimal("0.05"), new BigDecimal("-0.02"), new BigDecimal("0.07"),
                 "historical-ohlc-v1", List.of("historical-ohlc-v1"));
        String second = MarketOutcomeFingerprint.fingerprint(
                MarketOutcomeCalculator.DEFINITION_VERSION, marketId, T0,
                OutcomeHorizon.oneHour(), "reference", "future",
                 ObservedMarketOutcomeStatus.AVAILABLE, new BigDecimal("100"), new BigDecimal("102"),
                 new BigDecimal("105"), new BigDecimal("98"), new BigDecimal("0.02"),
                 new BigDecimal("0.05"), new BigDecimal("-0.02"), new BigDecimal("0.07"),
                 "historical-ohlc-v1", List.of("historical-ohlc-v1"));

        assertThat(first).isEqualTo(second);
    }

    private OutcomeFutureObservation observation(
            String open, String close, String high, String low, String price, String fingerprint) {
        return new OutcomeFutureObservation(
                Instant.parse("2026-10-15T" + open), Instant.parse("2026-10-15T" + close),
                new BigDecimal(high), new BigDecimal(low), new BigDecimal(price), fingerprint);
    }
}
