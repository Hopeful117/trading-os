package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.OrderBookEvidence;
import com.hope.trading.market_data.model.OrderBookEvidenceOrigin;
import com.hope.trading.market_data.model.OrderBookEvidenceType;
import com.hope.trading.market_data.model.OrderBookLevel;
import com.hope.trading.market_data.model.OrderBookReplayResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderBookReplayServiceTest {
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final UUID SEQUENCE_ID = UUID.randomUUID();
    private static final Instant CUTOFF = Instant.parse("2026-10-16T12:00:00Z");

    @Test
    void replaysOnlyEvidenceKnownAtTheCutoff() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        when(repository.findTrusted(MARKET_ID, 25, CUTOFF)).thenReturn(List.of(
                evidence(OrderBookEvidenceType.SNAPSHOT, 0, CUTOFF.minusSeconds(10),
                        List.of(level("100", "1")), List.of(level("101", "1"))),
                evidence(OrderBookEvidenceType.DELTA, 1, CUTOFF.minusSeconds(1),
                        List.of(level("100", "2")), List.of())
        ));

        OrderBookReplayResult result = new OrderBookReplayService(repository).replay(MARKET_ID, 25, CUTOFF);

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
        when(repository.findTrusted(MARKET_ID, 25, CUTOFF)).thenReturn(List.of());

        OrderBookReplayResult result = new OrderBookReplayService(repository).replay(MARKET_ID, 25, CUTOFF);

        assertThat(result.status()).isEqualTo(OrderBookReplayResult.ReplayStatus.UNAVAILABLE);
        assertThat(result.reason()).contains("No trusted snapshot");
    }

    @Test
    void rejectsInvalidReplayArguments() {
        OrderBookReplayService service = new OrderBookReplayService(mock(OrderBookEvidenceRepository.class));

        assertThatThrownBy(() -> service.replay(null, 25, CUTOFF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void marksNonContiguousReplayAsInvalid() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        when(repository.findTrusted(MARKET_ID, 25, CUTOFF)).thenReturn(List.of(
                evidence(OrderBookEvidenceType.SNAPSHOT, 0, CUTOFF.minusSeconds(10),
                        List.of(level("100", "1")), List.of(level("101", "1"))),
                evidence(OrderBookEvidenceType.DELTA, 2, CUTOFF.minusSeconds(1),
                        List.of(level("100", "2")), List.of())
        ));

        OrderBookReplayResult result = new OrderBookReplayService(repository).replay(MARKET_ID, 25, CUTOFF);

        assertThat(result.status()).isEqualTo(OrderBookReplayResult.ReplayStatus.INVALID);
        assertThat(result.reason()).contains("not contiguous");
    }

    @Test
    void rejectsUnexpectedSnapshotAfterReplayStart() {
        OrderBookEvidenceRepository repository = mock(OrderBookEvidenceRepository.class);
        when(repository.findTrusted(MARKET_ID, 25, CUTOFF)).thenReturn(List.of(
                evidence(OrderBookEvidenceType.SNAPSHOT, 0, CUTOFF.minusSeconds(1),
                        List.of(level("100", "1")), List.of(level("101", "1"))),
                evidence(OrderBookEvidenceType.SNAPSHOT, 1, CUTOFF.minusSeconds(10),
                        List.of(level("100", "2")), List.of(level("101", "1")))
        ));

        OrderBookReplayResult result = new OrderBookReplayService(repository).replay(MARKET_ID, 25, CUTOFF);

        assertThat(result.status()).isEqualTo(OrderBookReplayResult.ReplayStatus.INVALID);
        assertThat(result.reason()).contains("Unexpected non-delta");
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
