package com.hope.trading.market_data.service;

import com.hope.trading.market_data.config.OrderBookReplayProperties;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.OrderBookEvidence;
import com.hope.trading.market_data.model.OrderBookEvidenceOrigin;
import com.hope.trading.market_data.model.OrderBookEvidenceType;
import com.hope.trading.market_data.model.OrderBookLevel;
import com.hope.trading.market_data.model.OrderBookReplayArtifact;
import com.hope.trading.market_data.model.OrderBookReplayResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderBookReplayServiceTest {
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final UUID SEQUENCE_ID = UUID.randomUUID();
    private static final Instant CUTOFF = Instant.parse("2026-10-16T12:00:00Z");

    @Test
    void replaysOnlyEvidenceKnownAtTheCutoff() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        stubReplay(repository, List.of(
                evidence(OrderBookEvidenceType.SNAPSHOT, 0, CUTOFF.minusSeconds(10),
                        List.of(level("100", "1")), List.of(level("101", "1"))),
                evidence(OrderBookEvidenceType.DELTA, 1, CUTOFF.minusSeconds(1),
                        List.of(level("100", "2")), List.of())));

        OrderBookReplayResult result = service(repository).replay(MARKET_ID, 25, CUTOFF);

        assertThat(result.status()).isEqualTo(OrderBookReplayResult.ReplayStatus.AVAILABLE);
        assertThat(result.sequenceId()).isEqualTo(SEQUENCE_ID);
        assertThat(result.sourceStartOrdinal()).isZero();
        assertThat(result.sourceEndOrdinal()).isEqualTo(1L);
        assertThat(result.sourceEvidenceFingerprint()).isNotBlank();
        assertThat(result.bids()).containsExactly(level("100", "2"));
        assertThat(result.asks()).containsExactly(level("101", "1"));
    }

    @Test
    void refusesReplayWithoutATrustedSnapshot() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        stubReplay(repository, List.of());

        OrderBookReplayResult result = service(repository).replay(MARKET_ID, 25, CUTOFF);

        assertThat(result.status()).isEqualTo(OrderBookReplayResult.ReplayStatus.UNAVAILABLE);
        assertThat(result.reason()).contains("No trusted snapshot");
    }

    @Test
    void rejectsInvalidReplayArguments() {
        OrderBookReplayService service = service(mock(OrderBookEvidenceRepository.class));

        assertThatThrownBy(() -> service.replay(null, 25, CUTOFF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void marksNonContiguousReplayAsInvalid() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        stubReplay(repository, List.of(
                evidence(OrderBookEvidenceType.SNAPSHOT, 0, CUTOFF.minusSeconds(10),
                        List.of(level("100", "1")), List.of(level("101", "1"))),
                evidence(OrderBookEvidenceType.DELTA, 2, CUTOFF.minusSeconds(1),
                        List.of(level("100", "2")), List.of())));

        OrderBookReplayResult result = service(repository).replay(MARKET_ID, 25, CUTOFF);

        assertThat(result.status()).isEqualTo(OrderBookReplayResult.ReplayStatus.INVALID);
        assertThat(result.reason()).contains("not contiguous");
    }

    @Test
    void rejectsUnexpectedSnapshotAfterReplayStart() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        stubReplay(repository, List.of(
                evidence(OrderBookEvidenceType.SNAPSHOT, 0, CUTOFF.minusSeconds(1),
                        List.of(level("100", "1")), List.of(level("101", "1"))),
                evidence(OrderBookEvidenceType.SNAPSHOT, 1, CUTOFF.minusSeconds(10),
                        List.of(level("100", "2")), List.of(level("101", "1")))));

        OrderBookReplayResult result = service(repository).replay(MARKET_ID, 25, CUTOFF);

        assertThat(result.status()).isEqualTo(OrderBookReplayResult.ReplayStatus.INVALID);
        assertThat(result.reason()).contains("Unexpected non-delta");
    }

    @Test
    void failsClosedWhenReplayEvidenceLimitIsExceeded() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        OrderBookReplayProperties properties = new OrderBookReplayProperties();
        properties.setMaximumEvidenceCount(1);
        OrderBookEvidence snapshot = evidence(OrderBookEvidenceType.SNAPSHOT, 0, CUTOFF.minusSeconds(1),
                List.of(level("100", "1")), List.of(level("101", "1")));
        OrderBookEvidence delta = evidence(OrderBookEvidenceType.DELTA, 1, CUTOFF,
                List.of(level("100", "2")), List.of());
        when(repository.findTrustedReplayBase(MARKET_ID, 25, CUTOFF)).thenReturn(Optional.of(snapshot));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<OrderBookEvidence> consumer = invocation.getArgument(6);
            consumer.accept(delta);
            return 1;
        }).when(repository).streamTrustedEvidenceAfter(any(), anyInt(), any(), anyLong(), any(), anyInt(), any());

        OrderBookReplayResult result = new OrderBookReplayService(
                repository, mock(OrderBookRetentionRepository.class), properties)
                .replay(MARKET_ID, 25, CUTOFF);

        assertThat(result.status()).isEqualTo(OrderBookReplayResult.ReplayStatus.INVALID);
        assertThat(result.reason()).contains("limit exceeded");
    }

    @Test
    void returnsTrustedMatchingArtifactWithoutReadingCanonicalEvidence() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        OrderBookRetentionRepository retention = mock(OrderBookRetentionRepository.class);
        when(retention.findArtifact("research:sample:1")).thenReturn(java.util.Optional.of(artifact("TRUSTED")));
        when(repository.isCurrentEligibleSequence(SEQUENCE_ID, MARKET_ID, 25, CUTOFF)).thenReturn(true);
        when(retention.matchesArtifactFingerprint(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(retention.matchesPersistedProvenance(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        OrderBookReplayResult result = new OrderBookReplayService(repository, retention)
                .replay(MARKET_ID, 25, CUTOFF, "research:sample:1");

        assertThat(result.status()).isEqualTo(OrderBookReplayResult.ReplayStatus.AVAILABLE);
        assertThat(result.sourceStartOrdinal()).isZero();
        assertThat(result.sourceEndOrdinal()).isEqualTo(1L);
        org.mockito.Mockito.verify(repository).isCurrentEligibleSequence(SEQUENCE_ID, MARKET_ID, 25, CUTOFF);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
                .findTrustedReplayBase(MARKET_ID, 25, CUTOFF);
    }

    @Test
    void fallsBackToCanonicalReplayWhenArtifactDoesNotMatchRequest() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        OrderBookRetentionRepository retention = mock(OrderBookRetentionRepository.class);
        when(retention.findArtifact("research:sample:1")).thenReturn(java.util.Optional.of(
                artifact(CUTOFF.minusSeconds(1), "TRUSTED")));
        stubReplay(repository, List.of(
                evidence(OrderBookEvidenceType.SNAPSHOT, 0, CUTOFF.minusSeconds(10),
                        List.of(level("100", "1")), List.of(level("101", "1")))));

        OrderBookReplayResult result = new OrderBookReplayService(repository, retention)
                .replay(MARKET_ID, 25, CUTOFF, "research:sample:1");

        assertThat(result.status()).isEqualTo(OrderBookReplayResult.ReplayStatus.AVAILABLE);
        org.mockito.Mockito.verify(repository).findTrustedReplayBase(MARKET_ID, 25, CUTOFF);
    }

    @Test
    void fallsBackWhenArtifactIsUntrustedOrIncomplete() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        OrderBookRetentionRepository retention = mock(OrderBookRetentionRepository.class);
        when(retention.findArtifact("research:sample:1")).thenReturn(java.util.Optional.of(
                artifactWithLatestOccurredAt(null, "UNVERIFIED")));
        stubReplay(repository, List.of(
                evidence(OrderBookEvidenceType.SNAPSHOT, 0, CUTOFF.minusSeconds(10),
                        List.of(level("100", "1")), List.of(level("101", "1")))));

        OrderBookReplayResult result = new OrderBookReplayService(repository, retention)
                .replay(MARKET_ID, 25, CUTOFF, "research:sample:1");

        assertThat(result.status()).isEqualTo(OrderBookReplayResult.ReplayStatus.AVAILABLE);
        org.mockito.Mockito.verify(repository).findTrustedReplayBase(MARKET_ID, 25, CUTOFF);
    }

    private OrderBookReplayService service(OrderBookEvidenceRepository repository) {
        return new OrderBookReplayService(repository, mock(OrderBookRetentionRepository.class));
    }

    private void stubReplay(OrderBookEvidenceRepository repository, List<OrderBookEvidence> evidence) {
        if (evidence.isEmpty()) {
            when(repository.findTrustedReplayBase(MARKET_ID, 25, CUTOFF)).thenReturn(Optional.empty());
            return;
        }
        when(repository.findTrustedReplayBase(MARKET_ID, 25, CUTOFF))
                .thenReturn(Optional.of(evidence.getFirst()));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<OrderBookEvidence> consumer = invocation.getArgument(6);
            evidence.stream().skip(1).forEach(consumer);
            return evidence.size() - 1;
        }).when(repository).streamTrustedEvidenceAfter(any(), anyInt(), any(), anyLong(), any(), anyInt(), any());
    }

    private OrderBookReplayArtifact artifact(String trustStatus) {
        return artifact(CUTOFF, trustStatus);
    }

    private OrderBookReplayArtifact artifactWithLatestOccurredAt(Instant latestOccurredAt, String trustStatus) {
        return new OrderBookReplayArtifact(UUID.randomUUID(), "research:sample:1", "artifact-fingerprint",
                MARKET_ID, 25, SEQUENCE_ID, CUTOFF, latestOccurredAt, CUTOFF,
                0L, 1L, "KRAKEN", "PF_XBTUSD", "order-book-normalization-v1",
                "order-book-replay-artifact-v2", "source-fingerprint", trustStatus,
                List.of("source-fingerprint"), List.of(level("100", "2")),
                List.of(level("101", "1")), "test", Instant.now(Clock.systemUTC()));
    }

    private OrderBookReplayArtifact artifact(Instant cutoff, String trustStatus) {
        return new OrderBookReplayArtifact(UUID.randomUUID(), "research:sample:1", "artifact-fingerprint",
                MARKET_ID, 25, SEQUENCE_ID, cutoff, CUTOFF.minusSeconds(1), CUTOFF,
                0L, 1L, "KRAKEN", "PF_XBTUSD", "order-book-normalization-v1",
                "order-book-replay-artifact-v2", "source-fingerprint", trustStatus,
                List.of("source-fingerprint"), List.of(level("100", "2")),
                List.of(level("101", "1")), "test", Instant.now(Clock.systemUTC()));
    }

    private OrderBookEvidence evidence(OrderBookEvidenceType type, long ordinal, Instant knownAt,
                                       List<OrderBookLevel> bids, List<OrderBookLevel> asks) {
        OrderBookEvidence value = new OrderBookEvidence(UUID.randomUUID(), MARKET_ID, MarketProvider.KRAKEN,
                "BTC/USD", "PF_XBTUSD", 25, type, OrderBookEvidenceOrigin.PROVIDER, null, null,
                SEQUENCE_ID, ordinal,
                ordinal, knownAt, knownAt, bids, asks, null,
                OrderBookEvidenceFingerprint.NORMALIZATION_VERSION, "pending");
        return new OrderBookEvidence(value.evidenceId(), value.marketId(), value.provider(), value.symbol(),
                value.providerSymbol(), value.depth(), value.evidenceType(), value.origin(),
                value.sourceStartOrdinal(), value.sourceEndOrdinal(), value.sequenceId(),
                value.sequenceOrdinal(), value.providerSequence(), value.occurredAt(), value.knownAt(), value.bids(),
                value.asks(), value.checksum(), value.normalizationVersion(),
                OrderBookEvidenceFingerprint.fingerprint(value));
    }

    private OrderBookLevel level(String price, String quantity) {
        return new OrderBookLevel(new BigDecimal(price), new BigDecimal(quantity));
    }
}
