package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.HistoricalOhlcEvidence;
import com.hope.trading.market_data.model.HistoricalOhlcEvidenceOrigin;
import com.hope.trading.market_data.model.HistoricalOhlcEvidencePolicy;
import com.hope.trading.market_data.model.HistoricalOhlcRange;
import com.hope.trading.market_data.model.HistoricalOhlcRangeStatus;
import com.hope.trading.market_data.model.OhlcInterval;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoricalOhlcRangeCalculatorTest {
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final Instant FROM = Instant.parse("2026-10-15T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-15T11:00:00Z");

    @Test
    void returnsAvailableForFourCompleteCandles() {
        HistoricalOhlcRange result = calculate(
                evidence("10:00", "one"),
                evidence("10:15", "two"),
                evidence("10:30", "three"),
                evidence("10:45", "four"));

        assertThat(result.status()).isEqualTo(HistoricalOhlcRangeStatus.AVAILABLE);
        assertThat(result.expectedObservationCount()).isEqualTo(4);
        assertThat(result.observedObservationCount()).isEqualTo(4);
        assertThat(result.gaps()).isEmpty();
        assertThat(result.observations()).extracting(HistoricalOhlcEvidence::sourceId)
                .containsExactly("one", "two", "three", "four");
    }

    @Test
    void excludesCandleBeforeNonAlignedStartAndReportsMissingExpectedIntervals() {
        Instant from = Instant.parse("2026-10-15T10:07:00Z");
        HistoricalOhlcRange result = HistoricalOhlcRangeCalculator.calculate(
                MARKET_ID, OhlcInterval.FIFTEEN_MINUTES, from, TO,
                HistoricalOhlcEvidencePolicy.CAPTURED,
                List.of(evidence("10:00", "before"), evidence("10:15", "first")));

        assertThat(result.expectedObservationCount()).isEqualTo(3);
        assertThat(result.observedObservationCount()).isEqualTo(1);
        assertThat(result.observations()).extracting(HistoricalOhlcEvidence::sourceId)
                .containsExactly("first");
        assertThat(result.gaps()).extracting(gap -> gap.openTime().toString())
                .containsExactly("2026-10-15T10:30:00Z", "2026-10-15T10:45:00Z");
        assertThat(result.status()).isEqualTo(HistoricalOhlcRangeStatus.INCOMPLETE);
    }

    @Test
    void returnsUnavailableWhenNoCapturedEvidenceExists() {
        HistoricalOhlcRange result = calculate();

        assertThat(result.status()).isEqualTo(HistoricalOhlcRangeStatus.UNAVAILABLE);
        assertThat(result.reason()).isEqualTo("NO_CAPTURED_EVIDENCE");
        assertThat(result.expectedObservationCount()).isEqualTo(4);
        assertThat(result.observedObservationCount()).isZero();
        assertThat(result.gaps()).hasSize(4);
    }

    @Test
    void returnsUnavailableWhenRangeHasNoCompleteInterval() {
        HistoricalOhlcRange result = HistoricalOhlcRangeCalculator.calculate(
                MARKET_ID, OhlcInterval.FIFTEEN_MINUTES, FROM,
                Instant.parse("2026-10-15T10:05:00Z"),
                HistoricalOhlcEvidencePolicy.CAPTURED, List.of());

        assertThat(result.status()).isEqualTo(HistoricalOhlcRangeStatus.UNAVAILABLE);
        assertThat(result.reason()).isEqualTo("NO_COMPLETE_INTERVAL");
        assertThat(result.expectedObservationCount()).isZero();
        assertThat(result.gaps()).isEmpty();
    }

    @Test
    void excludesCandleExceedingExactEndBoundary() {
        HistoricalOhlcRange result = HistoricalOhlcRangeCalculator.calculate(
                MARKET_ID, OhlcInterval.FIFTEEN_MINUTES, FROM,
                Instant.parse("2026-10-15T10:59:59Z"),
                HistoricalOhlcEvidencePolicy.CAPTURED,
                List.of(evidence("10:00", "one"), evidence("10:15", "two"),
                        evidence("10:30", "three"), evidence("10:45", "four")));

        assertThat(result.expectedObservationCount()).isEqualTo(3);
        assertThat(result.observedObservationCount()).isEqualTo(3);
        assertThat(result.observations()).extracting(HistoricalOhlcEvidence::sourceId)
                .containsExactly("one", "two", "three");
        assertThat(result.status()).isEqualTo(HistoricalOhlcRangeStatus.AVAILABLE);
    }

    @Test
    void filtersBackfillSyntheticAndOpenEvidenceAndReportsFirstAndLastGaps() {
        HistoricalOhlcRange result = calculate(
                evidence("10:00", "backfill", HistoricalOhlcEvidenceOrigin.HISTORICAL_BACKFILL,
                        true, false),
                evidence("10:15", "synthetic", HistoricalOhlcEvidenceOrigin.CAPTURED,
                        true, true),
                evidence("10:30", "open", HistoricalOhlcEvidenceOrigin.CAPTURED,
                        false, false));

        assertThat(result.status()).isEqualTo(HistoricalOhlcRangeStatus.UNAVAILABLE);
        assertThat(result.observations()).isEmpty();
        assertThat(result.gaps()).extracting(gap -> gap.openTime().toString())
                .containsExactly("2026-10-15T10:00:00Z", "2026-10-15T10:15:00Z",
                        "2026-10-15T10:30:00Z", "2026-10-15T10:45:00Z");
    }

    @Test
    void selectsLatestCorrectionRegardlessOfCandidateOrder() {
        HistoricalOhlcEvidence older = evidence("10:15", "older",
                Instant.parse("2026-10-15T10:16:00Z"), HistoricalOhlcEvidenceOrigin.CAPTURED,
                true, false, "historical-ohlc-v1");
        HistoricalOhlcEvidence newer = evidence("10:15", "newer",
                Instant.parse("2026-10-15T10:17:00Z"), HistoricalOhlcEvidenceOrigin.CAPTURED,
                true, false, "historical-ohlc-v1");

        HistoricalOhlcRange result = HistoricalOhlcRangeCalculator.calculate(
                MARKET_ID, OhlcInterval.FIFTEEN_MINUTES, FROM, TO,
                HistoricalOhlcEvidencePolicy.CAPTURED, List.of(newer, older));

        assertThat(result.observations()).extracting(HistoricalOhlcEvidence::sourceId)
                .containsExactly("newer");
    }

    @Test
    void rejectsInvalidAndOversizedRequests() {
        assertThatThrownBy(() -> HistoricalOhlcRangeCalculator.calculate(
                MARKET_ID, OhlcInterval.FIFTEEN_MINUTES, TO, FROM,
                HistoricalOhlcEvidencePolicy.CAPTURED, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fromExclusive must be before toInclusive");

        Instant oversizedTo = FROM.plusSeconds(721 * 60L);
        assertThatThrownBy(() -> HistoricalOhlcRangeCalculator.calculate(
                MARKET_ID, OhlcInterval.ONE_MINUTE, FROM, oversizedTo,
                HistoricalOhlcEvidencePolicy.CAPTURED, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds 720");

        assertThatThrownBy(() -> HistoricalOhlcRangeCalculator.calculate(
                MARKET_ID, OhlcInterval.FIFTEEN_MINUTES, FROM, TO,
                HistoricalOhlcEvidencePolicy.STRICT_OBSERVED, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported historical OHLC range evidence policy");
    }

    @Test
    void fingerprintIsStableForSameFactsAndChangesForGaps() {
        HistoricalOhlcRange complete = calculate(
                evidence("10:00", "one"), evidence("10:15", "two"),
                evidence("10:30", "three"), evidence("10:45", "four"));
        HistoricalOhlcRange repeated = calculate(
                evidence("10:45", "four"), evidence("10:15", "two"),
                evidence("10:00", "one"), evidence("10:30", "three"));
        HistoricalOhlcRange incomplete = calculate(
                evidence("10:00", "one"), evidence("10:15", "two"),
                evidence("10:45", "four"));

        assertThat(repeated.evidenceFingerprint()).isEqualTo(complete.evidenceFingerprint());
        assertThat(incomplete.evidenceFingerprint()).isNotEqualTo(complete.evidenceFingerprint());

        HistoricalOhlcRange outsideRange = calculate(
                evidence("09:45", "outside"), evidence("10:00", "one"),
                evidence("10:15", "two"), evidence("10:30", "three"),
                evidence("10:45", "four"));
        assertThat(outsideRange.evidenceFingerprint()).isEqualTo(complete.evidenceFingerprint());

        HistoricalOhlcEvidence alternateNormalization = evidence(
                "10:00", "one", Instant.parse("2026-10-15T10:01:00Z"),
                HistoricalOhlcEvidenceOrigin.CAPTURED, true, false, "historical-ohlc-v2");
        HistoricalOhlcRange changedNormalization = calculate(
                alternateNormalization, evidence("10:15", "two"),
                evidence("10:30", "three"), evidence("10:45", "four"));
        assertThat(changedNormalization.evidenceFingerprint())
                .isNotEqualTo(complete.evidenceFingerprint());
    }

    private HistoricalOhlcRange calculate(HistoricalOhlcEvidence... evidence) {
        return HistoricalOhlcRangeCalculator.calculate(
                MARKET_ID, OhlcInterval.FIFTEEN_MINUTES, FROM, TO,
                HistoricalOhlcEvidencePolicy.CAPTURED, List.of(evidence));
    }

    private HistoricalOhlcEvidence evidence(
            String openTime,
            String sourceId,
            HistoricalOhlcEvidenceOrigin origin,
            boolean closed,
            boolean synthetic
    ) {
        return evidence(openTime, sourceId, Instant.parse("2026-10-15T10:20:00Z"),
                origin, closed, synthetic, "historical-ohlc-v1");
    }

    private HistoricalOhlcEvidence evidence(
            String openTime,
            String sourceId,
            Instant knownAt,
            HistoricalOhlcEvidenceOrigin origin,
            boolean closed,
            boolean synthetic,
            String normalizationVersion
    ) {
        Instant open = Instant.parse("2026-10-15T" + openTime + ":00Z");
        return new HistoricalOhlcEvidence(
                UUID.randomUUID(), MARKET_ID, OhlcInterval.FIFTEEN_MINUTES, open,
                open.plusSeconds(900), new BigDecimal("100"), new BigDecimal("105"),
                new BigDecimal("95"), new BigDecimal("102"), new BigDecimal("10"),
                new BigDecimal("101"), 2, closed, synthetic, MarketProvider.KRAKEN,
                "BTC/USD", sourceId, null, knownAt, knownAt, knownAt,
                origin, normalizationVersion, String.format("%064x", sourceId.hashCode()));
    }

    private HistoricalOhlcEvidence evidence(String openTime, String sourceId) {
        Instant open = Instant.parse("2026-10-15T" + openTime + ":00Z");
        return evidence(openTime, sourceId, open.plusSeconds(901),
                HistoricalOhlcEvidenceOrigin.CAPTURED, true, false, "historical-ohlc-v1");
    }
}
