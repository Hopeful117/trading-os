package com.hope.trading.market_data.repository;

import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.helper.TradingStatus;
import com.hope.trading.market_data.model.HistoricalFundingEvidence;
import com.hope.trading.market_data.model.HistoricalFundingEvidenceOrigin;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketConstraints;
import com.hope.trading.market_data.model.MarketState;
import com.hope.trading.market_data.service.HistoricalFundingEvidenceFingerprint;
import com.hope.trading.market_data.service.HistoricalFundingEvidenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HistoricalFundingEvidencePersistenceTest {
    @Autowired
    private MarketRepository markets;
    @Autowired
    private HistoricalFundingEvidenceRepository evidenceRepository;

    @Test
    void strictObservedReadUsesOnlyEvidenceKnownAtCutoff() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        evidenceRepository.append(evidence(marketId, "2026-10-16T08:00:00Z", "0.0001",
                "2026-10-16T08:00:02Z", HistoricalFundingEvidenceOrigin.CAPTURED, "captured"));
        evidenceRepository.append(evidence(marketId, "2026-10-16T16:00:00Z", "0.0002",
                "2026-10-16T16:00:02Z", HistoricalFundingEvidenceOrigin.CAPTURED, "future"));
        evidenceRepository.append(evidence(marketId, "2026-10-16T12:00:00Z", "0.0003",
                "2026-10-16T12:00:02Z", HistoricalFundingEvidenceOrigin.HISTORICAL_BACKFILL, "backfill"));

        assertThat(evidenceRepository.findStrictObserved(
                marketId, Instant.parse("2026-10-16T13:00:00Z"), 10))
                .extracting(HistoricalFundingEvidence::sourceId)
                .containsExactly("captured");
    }

    @Test
    void duplicateFingerprintIsIdempotent() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        HistoricalFundingEvidence value = evidence(marketId, "2026-10-16T08:00:00Z", "0.0001",
                "2026-10-16T08:00:02Z", HistoricalFundingEvidenceOrigin.CAPTURED, "same");
        HistoricalFundingEvidence retry = new HistoricalFundingEvidence(
                 UUID.randomUUID(), value.marketId(), value.provider(), value.symbol(), value.providerSymbol(),
                 value.productType(), value.fundingTime(), value.effectiveFrom(), value.effectiveTo(),
                 value.fundingRate(), value.settlementAsset(), value.sourceId(), value.sourceUpdatedAt(),
                value.fetchedAt(), value.capturedAt(), value.knownAt(), value.origin(),
                value.normalizationVersion(), value.evidenceFingerprint());

        assertThat(evidenceRepository.append(value)).isTrue();
        assertThat(evidenceRepository.append(retry)).isFalse();
    }

    private HistoricalFundingEvidence evidence(UUID marketId, String fundingTime, String rate,
                                               String knownAt, HistoricalFundingEvidenceOrigin origin,
                                               String sourceId) {
        Instant funding = Instant.parse(fundingTime);
        Instant fetched = Instant.parse(knownAt);
        HistoricalFundingEvidence pending = new HistoricalFundingEvidence(
                UUID.randomUUID(), marketId, MarketProvider.KRAKEN, "BTC/USD", "PF_XBTUSD",
                InstrumentProductType.LINEAR_PERPETUAL, funding, funding.minusSeconds(1), funding.plusSeconds(1),
                new BigDecimal(rate), "USD", sourceId, null, fetched, fetched, fetched, origin,
                HistoricalFundingEvidenceFingerprint.NORMALIZATION_VERSION, "pending");
        return new HistoricalFundingEvidence(pending.evidenceId(), pending.marketId(), pending.provider(),
                pending.symbol(), pending.providerSymbol(), pending.productType(), pending.fundingTime(),
                pending.effectiveFrom(), pending.effectiveTo(), pending.fundingRate(), pending.settlementAsset(),
                pending.sourceId(), pending.sourceUpdatedAt(), pending.fetchedAt(), pending.capturedAt(),
                pending.knownAt(), pending.origin(), pending.normalizationVersion(),
                HistoricalFundingEvidenceFingerprint.fingerprint(pending));
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
                        .tradable(true).lastUpdated(Instant.parse("2026-10-16T07:00:00Z")).build())
                .build();
    }
}
