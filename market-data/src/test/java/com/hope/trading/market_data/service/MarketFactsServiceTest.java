package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketDataReadiness;
import com.hope.trading.market_data.model.MarketFactStatus;
import com.hope.trading.market_data.model.MarketFactsRequest;
import com.hope.trading.market_data.model.MarketFactsResponse;
import com.hope.trading.market_data.model.MarketHistorySnapshot;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketFactsServiceTest {
    private static final UUID MARKET_ID =
            UUID.fromString("c0791ad6-ec9a-4f24-9d5a-2c6f3f7f5d01");
    private static final Instant BOUNDARY = Instant.parse("2026-10-03T12:00:00Z");

    private final MarketService marketService = mock(MarketService.class);
    private final MarketHistoryService historyService = mock(MarketHistoryService.class);
    private final Market market = market();
    private MarketFactsService service;

    @BeforeEach
    void setUp() {
        when(marketService.findById(MARKET_ID)).thenReturn(Optional.of(market));
        service = new MarketFactsService(
                marketService,
                historyService,
                Clock.fixed(BOUNDARY, ZoneOffset.UTC));
    }

    @Test
    void calculatesQuoteNotionalFromCompletedObservedCandles() {
        MarketFactsRequest request = request(3);
        List<OhlcEvent> events = List.of(
                event("2026-10-03T11:57:00Z", true, "2", "100"),
                event("2026-10-03T11:58:00Z", true, "3", "100"),
                event("2026-10-03T11:59:00Z", true, "4", "100"));
        when(historyService.findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4))
                .thenReturn(new MarketHistorySnapshot(events, events));

        MarketFactsResponse response = service.find(request);

        assertThat(response.activity().quoteNotional()).isEqualByComparingTo("900");
        assertThat(response.activity().status()).isEqualTo(MarketFactStatus.AVAILABLE);
        assertThat(response.activity().observedCandleCount()).isEqualTo(3);
        assertThat(response.readiness().status()).isEqualTo(MarketFactStatus.AVAILABLE);
    }

    @Test
    void currentOpenProviderCandleDoesNotInvalidateCompletedCadence() {
        Instant unalignedBoundary = Instant.parse("2026-10-03T12:00:17Z");
        service = new MarketFactsService(
                marketService,
                historyService,
                Clock.fixed(unalignedBoundary, ZoneOffset.UTC));
        List<OhlcEvent> completed = java.util.stream.IntStream.rangeClosed(46, 59)
                .mapToObj(minute -> event(
                        "2026-10-03T11:" + minute + ":00Z", true, "1", "100"))
                .toList();
        OhlcEvent currentOpen = event("2026-10-03T12:00:00Z", false, "1", "100");
        List<OhlcEvent> providerEvidence = new java.util.ArrayList<>(completed);
        providerEvidence.add(currentOpen);
        when(historyService.findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 16))
                .thenReturn(new MarketHistorySnapshot(providerEvidence, providerEvidence));

        MarketFactsResponse response = service.find(new MarketFactsRequest(
                MARKET_ID, OhlcInterval.ONE_MINUTE, Duration.ofMinutes(15),
                15, 1, Duration.ofMinutes(5)));

        assertThat(response.readiness().expectedCompletedCandleCount()).isEqualTo(14);
        assertThat(response.readiness().observedCompletedCandles()).isEqualTo(14);
        assertThat(response.readiness().missingIntervalCount()).isZero();
        assertThat(response.readiness().syntheticCandleCount()).isZero();
        assertThat(response.readiness().cadenceViolationCount()).isZero();
        assertThat(response.readiness().status()).isEqualTo(MarketFactStatus.AVAILABLE);
    }

    @Test
    void missingCompletedCandleRemainsInsufficientWithCurrentOpenCandle() {
        OhlcEvent first = event("2026-10-03T11:57:00Z", true, "1", "100");
        OhlcEvent last = event("2026-10-03T11:59:00Z", true, "1", "100");
        OhlcEvent currentOpen = event("2026-10-03T12:00:00Z", false, "1", "100");
        List<OhlcEvent> evidence = List.of(first, last, currentOpen);
        when(historyService.findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4))
                .thenReturn(new MarketHistorySnapshot(evidence, evidence));

        MarketFactsResponse response = service.find(request(3));

        assertThat(response.readiness().missingIntervalCount()).isEqualTo(1);
        assertThat(response.readiness().cadenceViolationCount()).isZero();
        assertThat(response.readiness().status()).isEqualTo(MarketFactStatus.INSUFFICIENT_DATA);
    }

    @Test
    void shiftedCompletedCandleIsACadenceViolation() {
        List<OhlcEvent> evidence = List.of(
                event("2026-10-03T11:56:00Z", true, "1", "100"),
                event("2026-10-03T11:57:00Z", true, "1", "100"),
                event("2026-10-03T11:59:30Z", true, "1", "100"));
        when(historyService.findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4))
                .thenReturn(new MarketHistorySnapshot(evidence, evidence));

        MarketFactsResponse response = service.find(request(3));

        assertThat(response.readiness().cadenceViolationCount()).isEqualTo(1);
        assertThat(response.readiness().status()).isEqualTo(MarketFactStatus.INSUFFICIENT_DATA);
    }

    @Test
    void zeroVolumeIsValidZeroContributionEvidence() {
        List<OhlcEvent> evidence = List.of(
                event("2026-10-03T11:57:00Z", true, "0", "0"),
                event("2026-10-03T11:58:00Z", true, "1", "100"),
                event("2026-10-03T11:59:00Z", true, "1", "100"));
        when(historyService.findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4))
                .thenReturn(new MarketHistorySnapshot(evidence, evidence));

        MarketFactsResponse response = service.find(request(3));

        assertThat(response.activity().quoteNotional()).isEqualByComparingTo("200");
        assertThat(response.activity().status()).isEqualTo(MarketFactStatus.AVAILABLE);
    }

    @Test
    void positiveVolumeWithZeroVwapIsIncomplete() {
        List<OhlcEvent> evidence = List.of(
                event("2026-10-03T11:57:00Z", true, "1", "0"),
                event("2026-10-03T11:58:00Z", true, "1", "100"),
                event("2026-10-03T11:59:00Z", true, "1", "100"));
        when(historyService.findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4))
                .thenReturn(new MarketHistorySnapshot(evidence, evidence));

        MarketFactsResponse response = service.find(request(3));

        assertThat(response.activity().status()).isEqualTo(MarketFactStatus.INSUFFICIENT_DATA);
        assertThat(response.activity().incompleteCandleCount()).isEqualTo(1);
    }

    @Test
    void excludesSyntheticCandlesAndReportsIncompleteEvidence() {
        OhlcEvent first = event("2026-10-03T11:57:00Z", true, "2", "100");
        OhlcEvent last = event("2026-10-03T11:59:00Z", true, "4", "100");
        OhlcEvent synthetic = new OhlcEvent(
                MARKET_ID, MarketProvider.KRAKEN, "XBT/EUR", OhlcInterval.ONE_MINUTE,
                Instant.parse("2026-10-03T11:58:00Z"),
                Instant.parse("2026-10-03T11:59:00Z"),
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("100"), 0,
                true, BOUNDARY, true, "synthetic", BOUNDARY);
        when(historyService.findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4))
                .thenReturn(new MarketHistorySnapshot(List.of(first, last),
                        List.of(first, synthetic, last)));

        MarketFactsResponse response = service.find(request(3));

        assertThat(response.activity().quoteNotional()).isEqualByComparingTo("600");
        assertThat(response.activity().status()).isEqualTo(MarketFactStatus.INSUFFICIENT_DATA);
        assertThat(response.readiness().syntheticCandleCount()).isEqualTo(1);
        assertThat(response.readiness().status()).isEqualTo(MarketFactStatus.INSUFFICIENT_DATA);
    }

    @Test
    void reportsStaleCompletedEvidence() {
        OhlcEvent stale = event("2026-10-03T11:00:00Z", true, "2", "100");
        when(historyService.findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 121))
                .thenReturn(new MarketHistorySnapshot(List.of(stale), List.of(stale)));

        MarketFactsResponse response = service.find(new MarketFactsRequest(
                MARKET_ID, OhlcInterval.ONE_MINUTE, Duration.ofMinutes(1),
                120, 1, Duration.ofMinutes(5)));

        assertThat(response.readiness().status()).isEqualTo(MarketFactStatus.STALE);
        assertThat(response.readiness().latestFetchedAt()).isEqualTo(BOUNDARY);
    }

    @Test
    void reusesEquivalentFactWithinFreshnessBoundary() {
        List<OhlcEvent> events = List.of(
                event("2026-10-03T11:57:00Z", true, "2", "100"),
                event("2026-10-03T11:58:00Z", true, "3", "100"),
                event("2026-10-03T11:59:00Z", true, "4", "100"));
        when(historyService.findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4))
                .thenReturn(new MarketHistorySnapshot(events, events));
        MarketFactsRequest request = request(3);

        MarketFactsResponse first = service.find(request);
        MarketFactsResponse second = service.find(request);

        assertThat(second).isSameAs(first);
        verify(historyService, times(1))
                .findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4);
    }

    @Test
    void providerFailureIsNotReportedAsAvailable() {
        when(historyService.findOhlcHistorySnapshot(any(), eq(OhlcInterval.ONE_MINUTE), eq(4)))
                .thenThrow(new IllegalStateException("provider unavailable"));

        MarketFactsResponse response = service.find(request(3));

        assertThat(response.activity().status()).isEqualTo(MarketFactStatus.UNAVAILABLE);
        assertThat(response.readiness().status()).isEqualTo(MarketFactStatus.UNAVAILABLE);
        verify(historyService).findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4);
    }

    @Test
    void incompleteRequestedLookbackIsNotAvailable() {
        OhlcEvent latest = event("2026-10-03T11:59:00Z", true, "4", "100");
        when(historyService.findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 121))
                .thenReturn(new MarketHistorySnapshot(List.of(latest), List.of(latest)));

        MarketFactsResponse response = service.find(new MarketFactsRequest(
                MARKET_ID, OhlcInterval.ONE_MINUTE, Duration.ofMinutes(1),
                120, 1, Duration.ofMinutes(5)));

        assertThat(response.readiness().status()).isEqualTo(MarketFactStatus.INSUFFICIENT_DATA);
        assertThat(response.readiness().missingIntervalCount()).isGreaterThan(0);
    }

    @Test
    void duplicateObservedCandleIsCountedOnce() {
        OhlcEvent first = event("2026-10-03T11:57:00Z", true, "2", "100");
        OhlcEvent duplicate = event("2026-10-03T11:57:00Z", true, "2", "100");
        OhlcEvent second = event("2026-10-03T11:58:00Z", true, "3", "100");
        OhlcEvent third = event("2026-10-03T11:59:00Z", true, "4", "100");
        List<OhlcEvent> events = List.of(first, duplicate, second, third);
        when(historyService.findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4))
                .thenReturn(new MarketHistorySnapshot(events, List.of(first, second, third)));

        MarketFactsResponse response = service.find(request(3));

        assertThat(response.activity().quoteNotional()).isEqualByComparingTo("900");
        assertThat(response.activity().duplicateCandleCount()).isEqualTo(1);
        assertThat(response.readiness().status()).isEqualTo(MarketFactStatus.AVAILABLE);
    }

    @Test
    void conflictingDuplicateCannotProduceAvailableFacts() {
        OhlcEvent first = event("2026-10-03T11:57:00Z", true, "2", "100");
        OhlcEvent conflict = event("2026-10-03T11:57:00Z", true, "20", "100");
        OhlcEvent second = event("2026-10-03T11:58:00Z", true, "3", "100");
        OhlcEvent third = event("2026-10-03T11:59:00Z", true, "4", "100");
        List<OhlcEvent> events = List.of(first, conflict, second, third);
        when(historyService.findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4))
                .thenReturn(new MarketHistorySnapshot(events, List.of(first, second, third)));

        MarketFactsResponse response = service.find(request(3));

        assertThat(response.activity().status()).isEqualTo(MarketFactStatus.INSUFFICIENT_DATA);
        assertThat(response.readiness().status()).isEqualTo(MarketFactStatus.INSUFFICIENT_DATA);
        assertThat(response.readiness().conflictingDuplicateCount()).isEqualTo(1);
    }

    @Test
    void failedAcquisitionIsNotCachedAndCanRecover() {
        List<OhlcEvent> events = List.of(
                event("2026-10-03T11:57:00Z", true, "2", "100"),
                event("2026-10-03T11:58:00Z", true, "3", "100"),
                event("2026-10-03T11:59:00Z", true, "4", "100"));
        when(historyService.findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4))
                .thenThrow(new IllegalStateException("temporary failure"))
                .thenReturn(new MarketHistorySnapshot(events, events));

        assertThat(service.find(request(3)).readiness().status())
                .isEqualTo(MarketFactStatus.UNAVAILABLE);
        assertThat(service.find(request(3)).readiness().status())
                .isEqualTo(MarketFactStatus.AVAILABLE);
        verify(historyService, times(2))
                .findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4);
    }

    @Test
    void cacheExpiryUsesEvidenceTimeNotGenerationTime() {
        MutableClock mutableClock = new MutableClock(BOUNDARY);
        service = new MarketFactsService(marketService, historyService, mutableClock);
        List<OhlcEvent> events = List.of(
                event("2026-10-03T11:57:00Z", true, "2", "100"),
                event("2026-10-03T11:58:00Z", true, "3", "100"),
                event("2026-10-03T11:59:00Z", true, "4", "100"));
        when(historyService.findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4))
                .thenReturn(new MarketHistorySnapshot(events, events));

        MarketFactsResponse first = service.find(request(3));
        mutableClock.advance(Duration.ofMinutes(5));
        assertThat(service.find(request(3))).isSameAs(first);
        mutableClock.advance(Duration.ofSeconds(1));
        assertThat(service.find(request(3))).isNotSameAs(first);
        verify(historyService, times(2))
                .findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4);
    }

    @Test
    void marketMetadataChangesInvalidateCacheIdentity() {
        List<OhlcEvent> events = List.of(
                event("2026-10-03T11:57:00Z", true, "2", "100"),
                event("2026-10-03T11:58:00Z", true, "3", "100"),
                event("2026-10-03T11:59:00Z", true, "4", "100"));
        when(historyService.findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4))
                .thenReturn(new MarketHistorySnapshot(events, events));

        service.find(request(3));
        market.setQuoteAsset("USD");
        service.find(request(3));

        verify(historyService, times(2))
                .findOhlcHistorySnapshot(MARKET_ID, OhlcInterval.ONE_MINUTE, 4);
    }

    private MarketFactsRequest request(int lookback) {
        return new MarketFactsRequest(
                MARKET_ID,
                OhlcInterval.ONE_MINUTE,
                Duration.ofMinutes(3),
                lookback,
                lookback,
                Duration.ofMinutes(5));
    }

    private Market market() {
        Market value = new Market();
        value.setMarketId(MARKET_ID);
        value.setProvider(MarketProvider.KRAKEN);
        value.setSymbol("XBT/EUR");
        value.setBaseAsset("XBT");
        value.setQuoteAsset("EUR");
        return value;
    }

    private OhlcEvent event(
            String openTime,
            boolean closed,
            String volume,
            String vwap
    ) {
        Instant open = Instant.parse(openTime);
        return new OhlcEvent(
                MARKET_ID, MarketProvider.KRAKEN, "XBT/EUR", OhlcInterval.ONE_MINUTE,
                open, open.plusSeconds(60), new BigDecimal("100"), new BigDecimal("101"),
                new BigDecimal("99"), new BigDecimal("100"), new BigDecimal(volume),
                new BigDecimal(vwap), 10, closed, open, false,
                "source-" + openTime, BOUNDARY);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
