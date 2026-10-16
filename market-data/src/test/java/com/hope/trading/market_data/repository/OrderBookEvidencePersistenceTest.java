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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

    private OrderBookEvidence evidence(UUID marketId, UUID sequenceId, long ordinal) {
        OrderBookEvidence value = new OrderBookEvidence(
                UUID.randomUUID(), marketId, MarketProvider.KRAKEN, "BTC/USD", "PF_XBTUSD", 25,
                OrderBookEvidenceType.SNAPSHOT, OrderBookEvidenceOrigin.PROVIDER, null, null,
                sequenceId, ordinal,
                100L, KNOWN_AT.minusSeconds(1), KNOWN_AT,
                List.of(level("100", "1")), List.of(level("101", "1")), null,
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
