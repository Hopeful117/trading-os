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
import com.hope.trading.market_data.model.OrderBookReplayResult;
import com.hope.trading.market_data.service.OrderBookEvidenceFingerprint;
import com.hope.trading.market_data.service.OrderBookEvidenceRepository;
import com.hope.trading.market_data.service.OrderBookRetentionService;
import com.hope.trading.market_data.config.OrderBookRetentionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderBookRetentionPersistenceTest {
    private static final Instant KNOWN_AT = Instant.parse("2026-10-20T12:00:00Z");

    @Autowired
    private MarketRepository markets;
    @Autowired
    private OrderBookEvidenceRepository evidenceRepository;
    @Autowired
    private OrderBookRetentionService retentionService;
    @Autowired
    private OrderBookRetentionProperties retentionProperties;

    @Test
    void unknownSequenceIsNotRetirementCandidate() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        evidenceRepository.append(evidence(marketId, sequenceId));
        evidenceRepository.sealActiveSequences(marketId, 25, KNOWN_AT.plusSeconds(1));

        assertThat(retentionService.previewRetirement().candidates()).isEmpty();
    }

    @Test
    void protectedDependencyExcludesSealedSequenceUntilReleased() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        OrderBookEvidence evidence = evidence(marketId, sequenceId);
        evidenceRepository.append(evidence);
        evidenceRepository.sealActiveSequences(marketId, 25, KNOWN_AT.plusSeconds(1));

        OrderBookReplayResult replay = new OrderBookReplayResult(
                OrderBookReplayResult.ReplayStatus.AVAILABLE, marketId, 25, sequenceId, KNOWN_AT,
                KNOWN_AT, KNOWN_AT, 0L, 0L, "KRAKEN", "PF_XBTUSD",
                 OrderBookEvidenceFingerprint.NORMALIZATION_VERSION, aggregateFingerprint(evidence.contentFingerprint()), "TRUSTED",
                 List.of(evidence.contentFingerprint()),
                 evidence.bids(), evidence.asks(), null);
        var artifact = retentionService.promote(replay, "research:sample:retention-test", "test");
        retentionService.protectDependency("dependency:retention-test", "TEST", "sample", artifact.artifactId(),
                null, "test protection");

        assertThat(retentionService.previewRetirement().candidates()).isEmpty();

        assertThat(retentionService.releaseDependency("dependency:retention-test", "test release")).isTrue();
        var preview = retentionService.previewRetirement();
        assertThat(preview.candidates())
                .extracting(candidate -> candidate.sequenceId())
                .containsExactly(sequenceId);

        retentionProperties.setDestructiveRetirementEnabled(true);
        assertThat(retentionService.retire(preview.operationFingerprint(), preview.candidates().getFirst()))
                .isEqualTo(1);
        assertThat(retentionService.previewRetirement().candidates()).isEmpty();
    }

    private OrderBookEvidence evidence(UUID marketId, UUID sequenceId) {
        OrderBookEvidence pending = new OrderBookEvidence(
                UUID.randomUUID(), marketId, MarketProvider.KRAKEN, "BTC/USD", "PF_XBTUSD", 25,
                OrderBookEvidenceType.SNAPSHOT, OrderBookEvidenceOrigin.PROVIDER, null, null,
                sequenceId, 0, 100L, KNOWN_AT.minusSeconds(1), KNOWN_AT,
                List.of(level("100", "1")), List.of(level("101", "1")), null,
                OrderBookEvidenceFingerprint.NORMALIZATION_VERSION, "pending");
        return new OrderBookEvidence(pending.evidenceId(), pending.marketId(), pending.provider(), pending.symbol(),
                pending.providerSymbol(), pending.depth(), pending.evidenceType(), pending.origin(),
                pending.sourceStartOrdinal(), pending.sourceEndOrdinal(), pending.sequenceId(),
                pending.sequenceOrdinal(), pending.providerSequence(), pending.occurredAt(), pending.knownAt(),
                pending.bids(), pending.asks(), pending.checksum(), pending.normalizationVersion(),
                OrderBookEvidenceFingerprint.fingerprint(pending));
    }

    private OrderBookLevel level(String price, String quantity) {
        return new OrderBookLevel(new BigDecimal(price), new BigDecimal(quantity));
    }

    private String aggregateFingerprint(String fingerprint) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(fingerprint.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
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
