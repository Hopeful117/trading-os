package com.hope.trading.market_data.repository;

import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.helper.TradingStatus;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketConstraints;
import com.hope.trading.market_data.model.MarketState;
import com.hope.trading.market_data.model.OrderBookEvidence;
import com.hope.trading.market_data.model.OrderBookEvidenceOrigin;
import com.hope.trading.market_data.model.OrderBookEvidenceType;
import com.hope.trading.market_data.model.OrderBookLevel;
import com.hope.trading.market_data.service.OrderBookEvidenceFingerprint;
import com.hope.trading.market_data.service.OrderBookEvidenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderBookEvidencePersistenceTest {
    private static final Instant KNOWN_AT = Instant.parse("2026-10-16T12:00:00Z");

    @Autowired
    private MarketRepository markets;
    @Autowired
    private OrderBookEvidenceRepository evidenceRepository;

    @Test
    void invalidatedSequenceIsExcludedAfterRestartEquivalentRepositoryRead() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        OrderBookEvidence evidence = evidence(marketId, sequenceId, 0);

        assertThat(evidenceRepository.append(evidence)).isTrue();
        assertThat(evidenceRepository.findTrusted(marketId, 25, KNOWN_AT)).containsExactly(evidence);

        evidenceRepository.invalidateSequence(sequenceId, "provider sequence gap", KNOWN_AT.plusSeconds(1));

        assertThat(evidenceRepository.findTrusted(marketId, 25, KNOWN_AT.plusSeconds(2))).isEmpty();
    }

    @Test
    void duplicateFingerprintIsIdempotent() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        OrderBookEvidence evidence = evidence(marketId, UUID.randomUUID(), 0);

        assertThat(evidenceRepository.append(evidence)).isTrue();
        assertThat(evidenceRepository.append(new OrderBookEvidence(
                UUID.randomUUID(), evidence.marketId(), evidence.provider(), evidence.symbol(),
                evidence.providerSymbol(), evidence.depth(), evidence.evidenceType(), evidence.origin(),
                evidence.sourceStartOrdinal(), evidence.sourceEndOrdinal(), evidence.sequenceId(),
                evidence.sequenceOrdinal(), evidence.providerSequence(),
                evidence.occurredAt(), evidence.knownAt(), evidence.bids(), evidence.asks(), evidence.checksum(),
                evidence.normalizationVersion(), evidence.contentFingerprint()))).isFalse();
    }

    @Test
    void rejectsEvidenceWithNonCanonicalFingerprint() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        OrderBookEvidence valid = evidence(marketId, UUID.randomUUID(), 0);
        OrderBookEvidence forged = new OrderBookEvidence(
                valid.evidenceId(), valid.marketId(), valid.provider(), valid.symbol(), valid.providerSymbol(),
                valid.depth(), valid.evidenceType(), valid.origin(), valid.sourceStartOrdinal(), valid.sourceEndOrdinal(),
                valid.sequenceId(), valid.sequenceOrdinal(), valid.providerSequence(), valid.occurredAt(),
                valid.knownAt(), valid.bids(), valid.asks(), valid.checksum(), valid.normalizationVersion(),
                "forged");

        assertThatThrownBy(() -> evidenceRepository.append(forged))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fingerprint");
    }

    @Test
    void rejectsEvidenceWithUnvalidatedChecksum() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        OrderBookEvidence valid = evidence(marketId, UUID.randomUUID(), 0);
        OrderBookEvidence withChecksum = new OrderBookEvidence(
                valid.evidenceId(), valid.marketId(), valid.provider(), valid.symbol(), valid.providerSymbol(),
                valid.depth(), valid.evidenceType(), valid.origin(), valid.sourceStartOrdinal(), valid.sourceEndOrdinal(),
                valid.sequenceId(), valid.sequenceOrdinal(), valid.providerSequence(), valid.occurredAt(),
                valid.knownAt(), valid.bids(), valid.asks(), 123L, valid.normalizationVersion(), "pending");
        withChecksum = new OrderBookEvidence(withChecksum.evidenceId(), withChecksum.marketId(),
                withChecksum.provider(), withChecksum.symbol(), withChecksum.providerSymbol(), withChecksum.depth(),
                withChecksum.evidenceType(), withChecksum.origin(), withChecksum.sourceStartOrdinal(),
                withChecksum.sourceEndOrdinal(), withChecksum.sequenceId(), withChecksum.sequenceOrdinal(),
                withChecksum.providerSequence(), withChecksum.occurredAt(), withChecksum.knownAt(),
                withChecksum.bids(), withChecksum.asks(), withChecksum.checksum(), withChecksum.normalizationVersion(),
                OrderBookEvidenceFingerprint.fingerprint(withChecksum));

        OrderBookEvidence candidate = withChecksum;
        assertThatThrownBy(() -> evidenceRepository.append(candidate))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void freshSnapshotSupersedesPriorActiveSequenceWithoutRewritingEvidence() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID oldSequenceId = UUID.randomUUID();
        UUID newSequenceId = UUID.randomUUID();
        OrderBookEvidence oldEvidence = evidence(marketId, oldSequenceId, 0, KNOWN_AT.minusSeconds(10));
        OrderBookEvidence newEvidence = evidence(marketId, newSequenceId, 0, KNOWN_AT);

        assertThat(evidenceRepository.append(oldEvidence)).isTrue();
        assertThat(evidenceRepository.appendSnapshotAsNewSequence(newEvidence, KNOWN_AT)).isTrue();

        assertThat(evidenceRepository.findTrusted(marketId, 25, KNOWN_AT.plusSeconds(1)))
                 .containsExactly(newEvidence);
    }

    @Test
    void sealedSequenceRemainsReplayableOnlyWithinItsKnownEvidenceInterval() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        OrderBookEvidence evidence = evidence(marketId, sequenceId, 0, KNOWN_AT);

        assertThat(evidenceRepository.append(evidence)).isTrue();
        evidenceRepository.sealActiveSequences(marketId, 25, KNOWN_AT.plusSeconds(1));

        assertThat(evidenceRepository.findTrusted(marketId, 25, KNOWN_AT)).containsExactly(evidence);
        assertThat(evidenceRepository.findTrusted(marketId, 25, KNOWN_AT.plusSeconds(2))).isEmpty();
    }

    @Test
    void replayReadStartsAtLatestCheckpointAndExcludesFutureEvidence() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        OrderBookEvidence initial = evidence(marketId, sequenceId, 0, KNOWN_AT,
                OrderBookEvidenceType.SNAPSHOT, OrderBookEvidenceOrigin.PROVIDER, level("100", "1"));
        OrderBookEvidence beforeCheckpoint = evidence(marketId, sequenceId, 1,
                KNOWN_AT.plusSeconds(1), OrderBookEvidenceType.DELTA, OrderBookEvidenceOrigin.PROVIDER,
                level("100", "2"));
        OrderBookEvidence checkpointDelta = evidence(marketId, sequenceId, 2,
                KNOWN_AT.plusSeconds(299), OrderBookEvidenceType.DELTA, OrderBookEvidenceOrigin.PROVIDER,
                level("100", "2.5"));
        OrderBookEvidence checkpoint = evidence(marketId, sequenceId, 2,
                KNOWN_AT.plusSeconds(300), OrderBookEvidenceType.SNAPSHOT, OrderBookEvidenceOrigin.CHECKPOINT,
                level("100", "3"));
        OrderBookEvidence afterCheckpoint = evidence(marketId, sequenceId, 3,
                KNOWN_AT.plusSeconds(301), OrderBookEvidenceType.DELTA, OrderBookEvidenceOrigin.PROVIDER,
                level("100", "4"));
        OrderBookEvidence future = evidence(marketId, sequenceId, 4,
                KNOWN_AT.plusSeconds(302), OrderBookEvidenceType.DELTA, OrderBookEvidenceOrigin.PROVIDER,
                level("100", "5"));

        assertThat(evidenceRepository.append(initial)).isTrue();
        assertThat(evidenceRepository.append(beforeCheckpoint)).isTrue();
        assertThat(evidenceRepository.append(checkpointDelta)).isTrue();
        assertThat(evidenceRepository.append(checkpoint)).isTrue();
        assertThat(evidenceRepository.append(afterCheckpoint)).isTrue();
        assertThat(evidenceRepository.append(future)).isTrue();

        assertThat(evidenceRepository.findTrusted(marketId, 25, KNOWN_AT.plusSeconds(301)))
                .extracting(OrderBookEvidence::sequenceOrdinal)
                .containsExactly(2L, 3L);

        List<Long> streamedOrdinals = new ArrayList<>();
        int streamed = evidenceRepository.streamTrustedEvidenceAfter(marketId, 25, sequenceId, 2L,
                KNOWN_AT.plusSeconds(302), 1,
                evidence -> streamedOrdinals.add(evidence.sequenceOrdinal()));

        assertThat(streamed).isEqualTo(2);
        assertThat(streamedOrdinals).containsExactly(3L, 4L);
    }

    @Test
    void invalidatedLatestSequenceDoesNotFallBackToOlderActiveEvidence() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID oldSequenceId = UUID.randomUUID();
        UUID newSequenceId = UUID.randomUUID();
        OrderBookEvidence oldEvidence = evidence(marketId, oldSequenceId, 0, KNOWN_AT.minusSeconds(10));
        OrderBookEvidence newEvidence = evidence(marketId, newSequenceId, 0, KNOWN_AT);

        assertThat(evidenceRepository.append(oldEvidence)).isTrue();
        assertThat(evidenceRepository.appendSnapshotAsNewSequence(newEvidence, KNOWN_AT)).isTrue();
        evidenceRepository.invalidateActiveSequences(marketId, 25, "stream terminated", KNOWN_AT.plusSeconds(1));

        assertThat(evidenceRepository.findTrusted(marketId, 25, KNOWN_AT.plusSeconds(2))).isEmpty();
    }

    private OrderBookEvidence evidence(UUID marketId, UUID sequenceId, long ordinal) {
        return evidence(marketId, sequenceId, ordinal, KNOWN_AT);
    }

    private OrderBookEvidence evidence(UUID marketId, UUID sequenceId, long ordinal, Instant knownAt) {
        return evidence(marketId, sequenceId, ordinal, knownAt, OrderBookEvidenceType.SNAPSHOT,
                OrderBookEvidenceOrigin.PROVIDER, level("100", "1"));
    }

    private OrderBookEvidence evidence(UUID marketId, UUID sequenceId, long ordinal, Instant knownAt,
                                       OrderBookEvidenceType type, OrderBookEvidenceOrigin origin,
                                       OrderBookLevel bid) {
        OrderBookEvidence value = new OrderBookEvidence(
                UUID.randomUUID(), marketId, MarketProvider.KRAKEN, "BTC/USD", "PF_XBTUSD", 25,
                type, origin, null, null,
                sequenceId, ordinal,
                100L, knownAt.minusSeconds(1), knownAt,
                List.of(bid), List.of(level("101", "1")), null,
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

    private Market market() {
        return Market.builder()
                .provider(MarketProvider.KRAKEN)
                .symbol("BTC/USD")
                .providerSymbol("PF_XBTUSD")
                .baseAsset("BTC")
                .quoteAsset("USD")
                .productType(InstrumentProductType.LINEAR_PERPETUAL)
                .marketConstraints(MarketConstraints.builder().build())
                .marketState(MarketState.builder().tradingStatus(TradingStatus.OPEN)
                        .tradable(true).lastUpdated(KNOWN_AT.minusSeconds(60)).build())
                .build();
    }
}
