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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
        when(repository.appendSnapshotAsNewSequence(any(), any())).thenReturn(true);
        OrderBookEvidenceCaptureService service = new OrderBookEvidenceCaptureService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.acceptSnapshot(delta(OrderBookDeltaType.SNAPSHOT, 10))).isTrue();
        assertThat(service.acceptUpdate(delta(OrderBookDeltaType.UPDATE, 12))).isFalse();
        verify(repository).invalidateActiveSequences(any(), anyInt(), any(), any());
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
        when(repository.appendSnapshotAsNewSequence(any(), any())).thenReturn(true);
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
        var context = service.diagnosticSnapshot(new com.hope.trading.market_data.model.OrderBookKey(MARKET_ID, 25));
        assertThat(context.lastOccurredAt()).isEqualTo(NOW);
        assertThat(context.lastKnownAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsSnapshotWhenPersistenceFails() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        when(repository.appendSnapshotAsNewSequence(any(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThat(new OrderBookEvidenceCaptureService(repository, Clock.fixed(NOW, ZoneOffset.UTC))
                .acceptSnapshot(delta(OrderBookDeltaType.SNAPSHOT, 10))).isFalse();
    }

    @Test
    void invalidatesSequenceAndStopsAfterPersistenceFailure() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        when(repository.appendSnapshotAsNewSequence(any(), any()))
                .thenReturn(true)
                .thenThrow(new IllegalStateException("database unavailable"));
        when(repository.append(any())).thenThrow(new IllegalStateException("database unavailable"));
        OrderBookEvidenceCaptureService service = new OrderBookEvidenceCaptureService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.acceptSnapshot(delta(OrderBookDeltaType.SNAPSHOT, 10))).isTrue();
        assertThat(service.acceptUpdate(delta(OrderBookDeltaType.UPDATE, 11))).isFalse();
        assertThat(service.acceptSnapshot(delta(OrderBookDeltaType.SNAPSHOT, 12))).isFalse();
        verify(repository).invalidateActiveSequences(any(), anyInt(), any(), any());
    }

    @Test
    void failsClosedWhenSequenceInvalidationCannotBePersisted() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        when(repository.append(any())).thenReturn(true);
        when(repository.appendSnapshotAsNewSequence(any(), any())).thenReturn(true);
        doThrow(new IllegalStateException("database unavailable"))
                .when(repository).invalidateActiveSequences(any(), anyInt(), any(), any());
        OrderBookEvidenceCaptureService service = new OrderBookEvidenceCaptureService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.acceptSnapshot(delta(OrderBookDeltaType.SNAPSHOT, 10))).isTrue();
        service.invalidate(new com.hope.trading.market_data.model.OrderBookKey(MARKET_ID, 25));
        assertThat(service.acceptUpdate(delta(OrderBookDeltaType.UPDATE, 11))).isFalse();
    }

    @Test
    void exposesActiveSequenceContextBeforeInvalidation() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        when(repository.append(any())).thenReturn(true);
        when(repository.appendSnapshotAsNewSequence(any(), any())).thenReturn(true);
        OrderBookEvidenceCaptureService service = new OrderBookEvidenceCaptureService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.acceptSnapshot(delta(OrderBookDeltaType.SNAPSHOT, 10))).isTrue();
        assertThat(service.acceptUpdate(delta(OrderBookDeltaType.UPDATE, 11))).isTrue();

        var context = service.diagnosticSnapshot(new com.hope.trading.market_data.model.OrderBookKey(MARKET_ID, 25));
        assertThat(context).isNotNull();
        assertThat(context.localOrdinal()).isEqualTo(1L);
        assertThat(context.providerSequence()).isEqualTo(11L);
        assertThat(context.lastOccurredAt()).isEqualTo(NOW.minusSeconds(1));
        assertThat(context.lastKnownAt()).isEqualTo(NOW);

        service.invalidate(new com.hope.trading.market_data.model.OrderBookKey(MARKET_ID, 25));

        assertThat(service.diagnosticSnapshot(new com.hope.trading.market_data.model.OrderBookKey(MARKET_ID, 25)))
                .isNull();
    }

    @Test
    void sealsSequenceAtTransportBoundaryWithoutInvalidatingHistoricalEvidence() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        when(repository.append(any())).thenReturn(true);
        when(repository.appendSnapshotAsNewSequence(any(), any())).thenReturn(true);
        OrderBookEvidenceCaptureService service = new OrderBookEvidenceCaptureService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.acceptSnapshot(delta(OrderBookDeltaType.SNAPSHOT, 10))).isTrue();
        service.seal(new com.hope.trading.market_data.model.OrderBookKey(MARKET_ID, 25));

        verify(repository).sealActiveSequences(MARKET_ID, 25, NOW);
        assertThat(service.diagnosticSnapshot(new com.hope.trading.market_data.model.OrderBookKey(MARKET_ID, 25)))
                .isNull();
    }

    @Test
    void serializesConcurrentSnapshotAcceptancePerMarketAndDepth() throws Exception {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger activeCalls = new AtomicInteger();
        AtomicInteger maxActiveCalls = new AtomicInteger();
        when(repository.appendSnapshotAsNewSequence(any(), any())).thenAnswer(invocation -> {
            int active = activeCalls.incrementAndGet();
            maxActiveCalls.accumulateAndGet(active, Math::max);
            entered.countDown();
            release.await();
            activeCalls.decrementAndGet();
            return true;
        });
        OrderBookEvidenceCaptureService service = new OrderBookEvidenceCaptureService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> service.acceptSnapshot(
                    delta(OrderBookDeltaType.SNAPSHOT, 10)));
            assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            Future<Boolean> second = executor.submit(() -> service.acceptSnapshot(
                    delta(OrderBookDeltaType.SNAPSHOT, 11)));
            release.countDown();

            assertThat(first.get()).isTrue();
            assertThat(second.get()).isTrue();
        }

        assertThat(maxActiveCalls).hasValue(1);
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
