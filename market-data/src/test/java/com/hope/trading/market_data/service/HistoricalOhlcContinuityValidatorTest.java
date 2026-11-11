package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.HistoricalOhlcContinuityReport;
import com.hope.trading.market_data.model.HistoricalClosureEvidence;
import com.hope.trading.market_data.model.HistoricalContinuityPolicy;
import com.hope.trading.market_data.model.HistoricalOhlcEvidence;
import com.hope.trading.market_data.model.HistoricalOhlcEvidenceOrigin;
import com.hope.trading.market_data.model.HistoricalOhlcTransition;
import com.hope.trading.market_data.model.HistoricalOhlcGapClassification;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.model.HistoricalTransitionClassification;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoricalOhlcContinuityValidatorTest {
    private static final UUID MARKET_ID = UUID.fromString("82852969-d309-481f-b6b8-078ff29d81e3");
    private static final Instant FROM = Instant.parse("2026-10-15T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-15T11:00:00Z");

    @Test
    void acceptsContinuousCompletedCandles() {
        HistoricalOhlcContinuityReport report = assess(
                evidence("10:00", true), evidence("10:15", true),
                evidence("10:30", true), evidence("10:45", true));

        assertThat(report.validObservationCount()).isEqualTo(4);
        assertThat(report.assessments()).isEmpty();
        assertThat(report.latestCompletedCandle()).isEqualTo(Instant.parse("2026-10-15T10:45:00Z"));
    }

    @Test
    void classifiesWeekendSlotsAsExpectedClosureOnlyForKnownForexSymbols() {
        var forex = HistoricalOhlcContinuityValidator.cTraderForexWeekendAvailability("EUR/USD");
        var unknown = HistoricalOhlcContinuityValidator.cTraderForexWeekendAvailability("XAU/USD");

        assertThat(forex.apply(Instant.parse("2026-10-17T12:00:00Z")))
                .isEqualTo(HistoricalOhlcGapClassification.EXPECTED_MARKET_CLOSURE);
        assertThat(unknown.apply(Instant.parse("2026-10-17T12:00:00Z")))
                .isEqualTo(HistoricalOhlcGapClassification.UNCERTAIN);
    }

    @Test
    void distinguishesMissingUnknownInvalidDuplicateAndCutoffEvidence() {
        HistoricalOhlcEvidence duplicate = evidence("10:00", true);
        HistoricalOhlcContinuityReport report = assess(
                duplicate, evidence("10:00", true), evidence("10:15", false),
                evidence("11:00", true), evidence("09:45", true));

        assertThat(report.count(HistoricalOhlcGapClassification.DUPLICATE)).isEqualTo(1);
        assertThat(report.count(HistoricalOhlcGapClassification.INVALID_CANDLE)).isEqualTo(1);
        assertThat(report.count(HistoricalOhlcGapClassification.UNCERTAIN)).isEqualTo(2);

        HistoricalOhlcContinuityReport missing = assess(evidence("10:00", true), evidence("10:30", true), evidence("10:45", true), evidence("11:00", true));
        assertThat(missing.count(HistoricalOhlcGapClassification.MISSING_DATA)).isEqualTo(1);
    }

    @Test
    void classifiesProviderBoundaryMismatchAndInvalidPrices() {
        HistoricalOhlcEvidence boundary = evidenceAt("10:07", true);

        HistoricalOhlcContinuityReport report = assess(evidence("10:00", true), boundary);

        assertThat(report.count(HistoricalOhlcGapClassification.INVALID_CANDLE)).isEqualTo(1);
        assertThatThrownBy(() -> new HistoricalOhlcEvidence(
                UUID.randomUUID(), MARKET_ID, OhlcInterval.FIFTEEN_MINUTES,
                Instant.parse("2026-10-15T10:15:00Z"), Instant.parse("2026-10-15T10:30:00Z"),
                new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("95"),
                new BigDecimal("102"), BigDecimal.ONE, BigDecimal.ONE, 1, true, false,
                MarketProvider.CTRADER, "1", "invalid", null,
                Instant.parse("2026-10-15T10:30:01Z"), Instant.parse("2026-10-15T10:30:01Z"),
                Instant.parse("2026-10-15T10:30:01Z"), HistoricalOhlcEvidenceOrigin.CAPTURED,
                "historical-ohlc-v1", UUID.randomUUID().toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OHLC prices are inconsistent");
    }

    @Test
    void rejectsUnboundedContinuityRange() {
        assertThatThrownBy(() -> HistoricalOhlcContinuityValidator.assess(
                MARKET_ID, "EUR/USD", OhlcInterval.ONE_MINUTE, FROM,
                FROM.plusSeconds(10_001 * 60L), List.of(),
                HistoricalOhlcContinuityValidator.unknownAvailability()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Continuity range exceeds");
    }

    @Test
    void classifiesScopedClosureWithoutCompressingTheElapsedTransition() {
        HistoricalOhlcEvidence previous = evidence("10:00", true);
        HistoricalOhlcEvidence next = evidence("10:30", true);
        HistoricalContinuityPolicy policy = new HistoricalContinuityPolicy(
                "test-v1", MARKET_ID, "EUR/USD", "test-venue", OhlcInterval.FIFTEEN_MINUTES,
                Instant.parse("2026-10-15T12:00:00Z"), true);
        HistoricalClosureEvidence closure = new HistoricalClosureEvidence(
                UUID.randomUUID(), MARKET_ID, "EUR/USD", OhlcInterval.FIFTEEN_MINUTES, "test-venue",
                "HOLIDAY", "test closure", Instant.parse("2026-10-15T10:15:00Z"),
                Instant.parse("2026-10-15T10:30:00Z"), "calendar", "v1",
                Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-10-16T00:00:00Z"),
                Instant.parse("2026-10-15T09:00:00Z"), "closure-fingerprint");

        HistoricalOhlcTransition result = HistoricalOhlcTransitionAssessor.assess(
                previous, next, policy, List.of(closure));

        assertThat(result.classification()).isEqualTo(HistoricalTransitionClassification.VERIFIED_CLOSURE);
        assertThat(result.actualElapsedDuration()).isEqualTo(java.time.Duration.ofMinutes(15));
        assertThat(result.closureEvidence()).isEqualTo(closure);
    }

    @Test
    void rejectsClosureEvidenceKnownAfterTheContinuityCutoff() {
        HistoricalContinuityPolicy policy = new HistoricalContinuityPolicy(
                "test-v1", MARKET_ID, "EUR/USD", "test-venue", OhlcInterval.FIFTEEN_MINUTES,
                Instant.parse("2026-10-15T09:00:00Z"), true);
        HistoricalClosureEvidence closure = new HistoricalClosureEvidence(
                UUID.randomUUID(), MARKET_ID, "EUR/USD", OhlcInterval.FIFTEEN_MINUTES, "test-venue",
                "HOLIDAY", "test closure", Instant.parse("2026-10-15T10:15:00Z"),
                Instant.parse("2026-10-15T10:30:00Z"), "calendar", "v1",
                Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-10-16T00:00:00Z"),
                Instant.parse("2026-10-15T10:00:00Z"), "closure-fingerprint");

        HistoricalOhlcTransition result = HistoricalOhlcTransitionAssessor.assess(
                evidence("10:00", true), evidence("10:30", true), policy, List.of(closure));

        assertThat(result.classification()).isEqualTo(HistoricalTransitionClassification.UNKNOWN_CLOSURE_EVIDENCE);
    }

    @Test
    void rejectsClosureEvidenceFromAnotherVenue() {
        HistoricalContinuityPolicy policy = new HistoricalContinuityPolicy(
                "test-v1", MARKET_ID, "EUR/USD", "test-venue", OhlcInterval.FIFTEEN_MINUTES,
                Instant.parse("2026-10-15T12:00:00Z"), true);
        HistoricalClosureEvidence closure = new HistoricalClosureEvidence(
                UUID.randomUUID(), MARKET_ID, "EUR/USD", OhlcInterval.FIFTEEN_MINUTES, "other-venue",
                "HOLIDAY", "test closure", Instant.parse("2026-10-15T10:15:00Z"),
                Instant.parse("2026-10-15T10:30:00Z"), "calendar", "v1",
                Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-10-16T00:00:00Z"),
                Instant.parse("2026-10-15T09:00:00Z"), "closure-fingerprint");

        HistoricalOhlcTransition result = HistoricalOhlcTransitionAssessor.assess(
                evidence("10:00", true), evidence("10:30", true), policy, List.of(closure));

        assertThat(result.classification()).isEqualTo(HistoricalTransitionClassification.UNKNOWN_CLOSURE_EVIDENCE);
    }

    private HistoricalOhlcContinuityReport assess(HistoricalOhlcEvidence... evidence) {
        return HistoricalOhlcContinuityValidator.assess(
                MARKET_ID, "EUR/USD", OhlcInterval.FIFTEEN_MINUTES, FROM, TO,
                List.of(evidence), open -> open.equals(Instant.parse("2026-10-15T10:15:00Z"))
                        ? HistoricalOhlcGapClassification.MISSING_DATA
                        : HistoricalOhlcGapClassification.UNCERTAIN);
    }

    private HistoricalOhlcEvidence evidence(String openTime, boolean closed) {
        Instant open = Instant.parse("2026-10-15T" + openTime + ":00Z");
        return new HistoricalOhlcEvidence(
                UUID.randomUUID(), MARKET_ID, OhlcInterval.FIFTEEN_MINUTES, open,
                open.plusSeconds(900), new BigDecimal("100"), new BigDecimal("105"),
                new BigDecimal("95"), new BigDecimal("102"), BigDecimal.ONE,
                BigDecimal.ONE, 1, closed, false, MarketProvider.CTRADER,
                "1", "test", null, open.plusSeconds(901), open.plusSeconds(901),
                open.plusSeconds(901), HistoricalOhlcEvidenceOrigin.CAPTURED,
                "historical-ohlc-v1", UUID.randomUUID().toString());
    }

    private HistoricalOhlcEvidence evidenceAt(String openTime, boolean closed) {
        Instant open = Instant.parse("2026-10-15T" + openTime + ":00Z");
        return new HistoricalOhlcEvidence(
                UUID.randomUUID(), MARKET_ID, OhlcInterval.FIFTEEN_MINUTES, open,
                open.plusSeconds(900), new BigDecimal("100"), new BigDecimal("105"),
                new BigDecimal("95"), new BigDecimal("102"), BigDecimal.ONE,
                BigDecimal.ONE, 1, closed, false, MarketProvider.CTRADER,
                "1", "test-boundary", null, open.plusSeconds(901), open.plusSeconds(901),
                open.plusSeconds(901), HistoricalOhlcEvidenceOrigin.CAPTURED,
                "historical-ohlc-v1", UUID.randomUUID().toString());
    }
}
