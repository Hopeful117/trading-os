package com.hope.trading.market_data.service;

import com.hope.trading.market_data.adapter.persistence.JpaHistoricalOhlcEvidenceRepository;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.HistoricalOhlcEvidence;
import com.hope.trading.market_data.model.HistoricalOhlcEvidenceOrigin;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.ThrowableAssert;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoricalOhlcEvidenceWriterTest {
    private static final Instant FETCHED_AT = Instant.parse("2026-10-15T10:15:03Z");
    private static final Instant CAPTURED_AT = Instant.parse("2026-10-15T10:15:04Z");

    @Test
    void persistsClosedProviderObservationWithConservativeKnownAt() {
        JpaHistoricalOhlcEvidenceRepository repository = mock(JpaHistoricalOhlcEvidenceRepository.class);
        OhlcEvent event = event(true, false, "100");
        HistoricalOhlcEvidenceWriter writer = new HistoricalOhlcEvidenceWriter(
                repository, Clock.fixed(CAPTURED_AT, ZoneOffset.UTC));
        HistoricalOhlcEvidence evidence = evidence(event);
        when(repository.append(any())).thenReturn(Optional.of(evidence));

        Optional<HistoricalOhlcEvidence> result = writer.appendCaptured(event);

        assertThat(result).contains(evidence);
        assertThat(evidence.origin()).hasToString("CAPTURED");
        assertThat(evidence.providerSymbol()).isEqualTo("PF_XBTUSD");
        assertThat(evidence.knownAt()).isEqualTo(CAPTURED_AT);
        verify(repository).append(any(HistoricalOhlcEvidence.class));
    }

    @Test
    void doesNotPersistFormingOrSyntheticEvidence() {
        JpaHistoricalOhlcEvidenceRepository repository = mock(JpaHistoricalOhlcEvidenceRepository.class);
        HistoricalOhlcEvidenceWriter writer = new HistoricalOhlcEvidenceWriter(
                repository, Clock.fixed(CAPTURED_AT, ZoneOffset.UTC));

        assertThat(writer.appendCaptured(event(false, false, "100"))).isEmpty();
        assertThat(writer.appendCaptured(event(true, true, "100"))).isEmpty();
        verify(repository, never()).append(any());
    }

    @Test
    void fingerprintDoesNotChangeWhenOnlyAcquisitionTimesChange() {
        OhlcEvent first = event(true, false, "100");
        OhlcEvent second = new OhlcEvent(
                first.marketId(), first.provider(), first.symbol(), first.interval(), first.openTime(),
                first.closeTime(), first.open(), first.high(), first.low(), first.close(), first.volume(),
                first.vwap(), first.trades(), first.closed(), first.occurredAt(), first.synthetic(),
                first.sourceId(), FETCHED_AT.plusSeconds(5));

        assertThat(HistoricalOhlcEvidenceFingerprint.captured(first))
                .isEqualTo(HistoricalOhlcEvidenceFingerprint.captured(second));
    }

    @Test
    void rejectsCapturedEvidenceWhenCapturedAtPrecedesFetchedAt() {
        OhlcEvent event = event(true, false, "100");
        HistoricalOhlcEvidenceWriter writer = new HistoricalOhlcEvidenceWriter(
                mock(JpaHistoricalOhlcEvidenceRepository.class), Clock.systemUTC());

        ThrowableAssert.ThrowingCallable append = () -> writer.append(event, HistoricalOhlcEvidenceOrigin.CAPTURED,
                FETCHED_AT.minusSeconds(1), FETCHED_AT);
        assertThatThrownBy(append)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capturedAt cannot precede fetchedAt");
    }

    @Test
    void rejectsCapturedEvidenceWhenKnownAtPrecedesCapturedAt() {
        OhlcEvent event = event(true, false, "100");
        HistoricalOhlcEvidenceWriter writer = new HistoricalOhlcEvidenceWriter(
                mock(JpaHistoricalOhlcEvidenceRepository.class), Clock.systemUTC());

        ThrowableAssert.ThrowingCallable append = () -> writer.append(event, HistoricalOhlcEvidenceOrigin.CAPTURED,
                CAPTURED_AT, CAPTURED_AT.minusSeconds(1));
        assertThatThrownBy(append)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("knownAt cannot precede capturedAt");
    }

    private HistoricalOhlcEvidence evidence(OhlcEvent event) {
        return new HistoricalOhlcEvidence(
                UUID.randomUUID(), event.marketId(), event.interval(), event.openTime(), event.closeTime(),
                event.open(), event.high(), event.low(), event.close(), event.volume(), event.vwap(),
                event.trades(), event.closed(), event.synthetic(), event.provider(), event.providerSymbol(),
                event.sourceId(), null, event.fetchedAt(), CAPTURED_AT, CAPTURED_AT,
                com.hope.trading.market_data.model.HistoricalOhlcEvidenceOrigin.CAPTURED,
                HistoricalOhlcEvidenceFingerprint.NORMALIZATION_VERSION,
                HistoricalOhlcEvidenceFingerprint.captured(event));
    }

    private OhlcEvent event(boolean closed, boolean synthetic, String open) {
        Instant openTime = Instant.parse("2026-10-15T10:00:00Z");
        return new OhlcEvent(
                UUID.randomUUID(), MarketProvider.KRAKEN, "BTC/USD", OhlcInterval.FIFTEEN_MINUTES,
                openTime, openTime.plusSeconds(900), new BigDecimal(open), new BigDecimal("105"),
                new BigDecimal("95"), new BigDecimal("102"), new BigDecimal("10"),
                new BigDecimal("101"), 2, closed, FETCHED_AT, synthetic,
                "KRAKEN:BTC/USD:FIFTEEN_MINUTES:" + openTime, FETCHED_AT, "PF_XBTUSD");
    }
}
