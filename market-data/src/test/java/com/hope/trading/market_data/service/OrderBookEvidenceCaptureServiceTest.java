package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.OrderBookDelta;
import com.hope.trading.market_data.model.OrderBookDeltaType;
import com.hope.trading.market_data.model.OrderBookLevel;
import com.hope.trading.market_data.model.OrderBookSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderBookEvidenceCaptureServiceTest {
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    @Test
    void startsASequenceAndRejectsAProviderGap() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        when(repository.append(any())).thenReturn(true);
        OrderBookEvidenceCaptureService service = new OrderBookEvidenceCaptureService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.acceptSnapshot(delta(OrderBookDeltaType.SNAPSHOT, 10))).isTrue();
        assertThat(service.acceptUpdate(delta(OrderBookDeltaType.UPDATE, 12))).isFalse();
        verify(repository).invalidateSequence(any(), any(), any());
    }

    @Test
    void rejectsUnexpectedChecksumsAndUpdatesWithoutSnapshot() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        OrderBookEvidenceCaptureService service = new OrderBookEvidenceCaptureService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.acceptSnapshot(deltaWithChecksum(123L))).isFalse();
        assertThat(service.acceptUpdate(delta(OrderBookDeltaType.UPDATE, 10))).isFalse();
    }

    @Test
    void persistsCheckpointWithSourceRange() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        when(repository.append(any())).thenReturn(true);
        OrderBookEvidenceCaptureService service = new OrderBookEvidenceCaptureService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));
        OrderBookDelta snapshot = delta(OrderBookDeltaType.SNAPSHOT, 10);
        OrderBookDelta update = delta(OrderBookDeltaType.UPDATE, 11);

        assertThat(service.acceptSnapshot(snapshot)).isTrue();
        assertThat(service.acceptUpdate(update)).isTrue();
        service.checkpoint(update, new OrderBookSnapshot(MARKET_ID, MarketProvider.KRAKEN, "BTC/USD", 25,
                update.bids(), update.asks(), new BigDecimal("100"), new BigDecimal("101"),
                new BigDecimal("1"), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, NOW));

        verify(repository).append(org.mockito.ArgumentMatchers.argThat(evidence ->
                evidence.origin() == com.hope.trading.market_data.model.OrderBookEvidenceOrigin.CHECKPOINT
                        && evidence.sourceStartOrdinal() == 0L
                && evidence.sourceEndOrdinal() == 1L));
    }

    @Test
    void rejectsSnapshotWhenPersistenceFails() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        when(repository.append(any())).thenThrow(new IllegalStateException("database unavailable"));

        assertThat(new OrderBookEvidenceCaptureService(repository, Clock.fixed(NOW, ZoneOffset.UTC))
                .acceptSnapshot(delta(OrderBookDeltaType.SNAPSHOT, 10))).isFalse();
    }

    @Test
    void invalidatesSequenceAndStopsAfterPersistenceFailure() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        when(repository.append(any())).thenReturn(true).thenThrow(new IllegalStateException("database unavailable"));
        OrderBookEvidenceCaptureService service = new OrderBookEvidenceCaptureService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.acceptSnapshot(delta(OrderBookDeltaType.SNAPSHOT, 10))).isTrue();
        assertThat(service.acceptUpdate(delta(OrderBookDeltaType.UPDATE, 11))).isFalse();
        assertThat(service.acceptSnapshot(delta(OrderBookDeltaType.SNAPSHOT, 12))).isFalse();
        verify(repository).invalidateSequence(any(), any(), any());
    }

    @Test
    void failsClosedWhenSequenceInvalidationCannotBePersisted() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        when(repository.append(any())).thenReturn(true);
        doThrow(new IllegalStateException("database unavailable"))
                .when(repository).invalidateSequence(any(), any(), any());
        OrderBookEvidenceCaptureService service = new OrderBookEvidenceCaptureService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.acceptSnapshot(delta(OrderBookDeltaType.SNAPSHOT, 10))).isTrue();
        service.invalidate(new com.hope.trading.market_data.model.OrderBookKey(MARKET_ID, 25));
        assertThat(service.acceptUpdate(delta(OrderBookDeltaType.UPDATE, 11))).isFalse();
    }

    private OrderBookDelta delta(OrderBookDeltaType type, long sequence) {
        return new OrderBookDelta(MARKET_ID, MarketProvider.KRAKEN, "BTC/USD", "PF_XBTUSD", 25,
                type, List.of(level("100", "1")), List.of(level("101", "1")),
                NOW.minusSeconds(1), null, sequence);
    }

    private OrderBookDelta deltaWithChecksum(long checksum) {
        return new OrderBookDelta(MARKET_ID, MarketProvider.KRAKEN, "BTC/USD", "PF_XBTUSD", 25,
                OrderBookDeltaType.SNAPSHOT, List.of(level("100", "1")), List.of(level("101", "1")),
                NOW.minusSeconds(1), checksum, 10L);
    }

    private OrderBookLevel level(String price, String quantity) {
        return new OrderBookLevel(
                new BigDecimal(price), new BigDecimal(quantity));
    }
}
