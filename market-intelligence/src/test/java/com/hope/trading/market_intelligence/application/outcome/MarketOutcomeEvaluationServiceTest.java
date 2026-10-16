package com.hope.trading.market_intelligence.application.outcome;

import com.hope.trading.market_intelligence.adapter.marketdata.HistoricalOhlcFutureEvidenceSource;
import com.hope.trading.market_intelligence.adapter.marketdata.HistoricalOhlcReferenceEvidenceSource;
import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeFutureObservation;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeReferenceEvidence;
import com.hope.trading.market_intelligence.domain.outcome.MarketOutcomeCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MarketOutcomeEvaluationServiceTest {
    private static final UUID MARKET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant T0 = Instant.parse("2026-10-15T10:00:00Z");

    @Test
    void returnsPendingBeforeMaturityWithoutAcquiringOrPersisting() {
        HistoricalOhlcReferenceEvidenceSource reference = mock(HistoricalOhlcReferenceEvidenceSource.class);
        HistoricalOhlcFutureEvidenceSource future = mock(HistoricalOhlcFutureEvidenceSource.class);
        ObservedMarketOutcomeRepository repository = mock(ObservedMarketOutcomeRepository.class);
        MarketOutcomeEvaluationService service = service(reference, future, repository,
                Instant.parse("2026-10-15T10:30:00Z"));

        var result = service.evaluate(request(OutcomeHorizon.oneHour()));

        assertThat(result.status()).isEqualTo(ObservedMarketOutcomeStatus.PENDING);
        assertThat(result.finalPrice()).isNull();
        verifyNoInteractions(reference, future, repository);
    }

    @Test
    void calculatesAndPersistsAtExactMaturity() {
        HistoricalOhlcReferenceEvidenceSource reference = mock(HistoricalOhlcReferenceEvidenceSource.class);
        HistoricalOhlcFutureEvidenceSource future = mock(HistoricalOhlcFutureEvidenceSource.class);
        ObservedMarketOutcomeRepository repository = mock(ObservedMarketOutcomeRepository.class);
        when(reference.findLatest(MARKET_ID, T0)).thenReturn(Optional.of(reference()));
        when(future.find(MARKET_ID, T0, T0.plusSeconds(3600))).thenReturn(availableRange());
        when(repository.saveIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MarketOutcomeEvaluationService service = service(reference, future, repository, T0.plusSeconds(3600));

        var result = service.evaluate(request(OutcomeHorizon.oneHour()));

        assertThat(result.status()).isEqualTo(ObservedMarketOutcomeStatus.AVAILABLE);
        assertThat(result.forwardReturn()).isEqualByComparingTo("0.02");
        assertThat(result.futureHighReturn()).isEqualByComparingTo("0.05");
        assertThat(result.futureLowReturn()).isEqualByComparingTo("-0.02");
        verify(repository).saveIfAbsent(any());
    }

    @Test
    void incompleteFutureProducesNoFinalMetrics() {
        HistoricalOhlcReferenceEvidenceSource reference = mock(HistoricalOhlcReferenceEvidenceSource.class);
        HistoricalOhlcFutureEvidenceSource future = mock(HistoricalOhlcFutureEvidenceSource.class);
        ObservedMarketOutcomeRepository repository = mock(ObservedMarketOutcomeRepository.class);
        when(reference.findLatest(MARKET_ID, T0)).thenReturn(Optional.of(reference()));
        when(future.find(MARKET_ID, T0, T0.plusSeconds(3600))).thenReturn(
                new HistoricalOhlcFutureEvidenceSource.HistoricalOhlcRangeSnapshot(
                        "INCOMPLETE", "MISSING_EXPECTED_INTERVALS", 4, 3,
                        List.of("2026-10-15T10:15:00Z..2026-10-15T10:30:00Z"),
                         "future-range", List.of("historical-ohlc-v1"), List.of()));
        when(repository.saveIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MarketOutcomeEvaluationService service = service(reference, future, repository, T0.plusSeconds(3600));

        var result = service.evaluate(request(OutcomeHorizon.oneHour()));

        assertThat(result.status()).isEqualTo(ObservedMarketOutcomeStatus.INCOMPLETE);
        assertThat(result.forwardReturn()).isNull();
        verify(repository).saveIfAbsent(any());
    }

    @Test
    void missingReferenceProducesUnavailableWithoutInventedPrice() {
        HistoricalOhlcReferenceEvidenceSource reference = mock(HistoricalOhlcReferenceEvidenceSource.class);
        HistoricalOhlcFutureEvidenceSource future = mock(HistoricalOhlcFutureEvidenceSource.class);
        ObservedMarketOutcomeRepository repository = mock(ObservedMarketOutcomeRepository.class);
        when(reference.findLatest(MARKET_ID, T0)).thenReturn(Optional.empty());
        when(repository.saveIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MarketOutcomeEvaluationService service = service(reference, future, repository, T0.plusSeconds(3600));

        var result = service.evaluate(request(OutcomeHorizon.oneHour()));

        assertThat(result.status()).isEqualTo(ObservedMarketOutcomeStatus.UNAVAILABLE);
        assertThat(result.referencePrice()).isNull();
        assertThat(result.finalPrice()).isNull();
        verifyNoInteractions(future);
    }

    private MarketOutcomeEvaluationService service(
            HistoricalOhlcReferenceEvidenceSource reference,
            HistoricalOhlcFutureEvidenceSource future,
            ObservedMarketOutcomeRepository repository,
            Instant now) {
        return new MarketOutcomeEvaluationService(reference, future, new MarketOutcomeCalculator(), repository,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private MarketOutcomeEvaluationRequest request(OutcomeHorizon horizon) {
        return new MarketOutcomeEvaluationRequest(MARKET_ID, T0, horizon, null);
    }

    private OutcomeReferenceEvidence reference() {
        return new OutcomeReferenceEvidence(new BigDecimal("100"), T0,
                T0, "reference-fingerprint", "historical-ohlc-v1");
    }

    private HistoricalOhlcFutureEvidenceSource.HistoricalOhlcRangeSnapshot availableRange() {
        return new HistoricalOhlcFutureEvidenceSource.HistoricalOhlcRangeSnapshot(
                "AVAILABLE", null, 4, 4, List.of(), "future-range", List.of("historical-ohlc-v1"), List.of(
                observation("10:00:00Z", "10:15:00Z", "103", "99", "102", "future-1"),
                observation("10:15:00Z", "10:30:00Z", "105", "101", "104", "future-2"),
                observation("10:30:00Z", "10:45:00Z", "104", "99", "100", "future-3"),
                observation("10:45:00Z", "11:00:00Z", "102", "98", "102", "future-4")));
    }

    private OutcomeFutureObservation observation(
            String open, String close, String high, String low, String price, String fingerprint) {
        return new OutcomeFutureObservation(
                Instant.parse("2026-10-15T" + open), Instant.parse("2026-10-15T" + close),
                new BigDecimal(high), new BigDecimal(low), new BigDecimal(price), fingerprint);
    }
}
