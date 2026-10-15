package com.hope.trading.market_data.repository;

import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.helper.TradingStatus;
import com.hope.trading.market_data.model.*;
import com.hope.trading.market_data.service.HistoricalOhlcEvidenceFingerprint;
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
class HistoricalOhlcEvidencePersistenceTest {
    @Autowired
    private MarketRepository markets;
    @Autowired
    private com.hope.trading.market_data.adapter.persistence.JpaHistoricalOhlcEvidenceRepository evidenceRepository;

    @Test
    void selectsOnlyEvidenceKnownAtCutoffAndKeepsCorrectionVersions() {
        Market market = markets.saveAndFlush(market());
        UUID marketId = market.getMarketId();
        Instant c1Open = Instant.parse("2026-10-15T09:45:00Z");
        Instant c2Open = Instant.parse("2026-10-15T10:00:00Z");
        evidenceRepository.append(evidence(marketId, c1Open, "100", "2026-10-15T10:00:02Z", "c1-a"));
        evidenceRepository.append(evidence(marketId, c2Open, "101", "2026-10-15T10:15:03Z", "c2-a"));
        evidenceRepository.append(evidence(marketId, c2Open, "102", "2026-10-15T12:00:00Z", "c2-b"));

        HistoricalOhlcSeries beforeCorrection = evidenceRepository.findAsOf(
                marketId, OhlcInterval.FIFTEEN_MINUTES,
                Instant.parse("2026-10-15T11:00:00Z"), 10,
                HistoricalOhlcEvidencePolicy.STRICT_OBSERVED);
        HistoricalOhlcSeries afterCorrection = evidenceRepository.findAsOf(
                marketId, OhlcInterval.FIFTEEN_MINUTES,
                Instant.parse("2026-10-15T13:00:00Z"), 10,
                HistoricalOhlcEvidencePolicy.STRICT_OBSERVED);

        assertThat(beforeCorrection.observations()).extracting(HistoricalOhlcEvidence::sourceId)
                .containsExactly("c1-a", "c2-a");
        assertThat(beforeCorrection.observations()).extracting(HistoricalOhlcEvidence::providerSymbol)
                .containsExactly("BTC/USD", "BTC/USD");
        assertThat(afterCorrection.observations()).extracting(HistoricalOhlcEvidence::sourceId)
                .containsExactly("c1-a", "c2-b");
    }

    @Test
    void isolatesMarketAndIntervalAndAppliesLimit() {
        UUID firstMarket = markets.saveAndFlush(market("BTC/USD", "BTC", "USD")).getMarketId();
        UUID secondMarket = markets.saveAndFlush(market("ETH/USD", "ETH", "USD")).getMarketId();
        evidenceRepository.append(evidence(firstMarket, Instant.parse("2026-10-15T09:45:00Z"), "100", "2026-10-15T10:00:02Z", "first-1"));
        evidenceRepository.append(evidence(firstMarket, Instant.parse("2026-10-15T10:00:00Z"), "101", "2026-10-15T10:15:02Z", "first-2"));
        evidenceRepository.append(evidence(secondMarket, Instant.parse("2026-10-15T09:45:00Z"), "100", "2026-10-15T10:00:02Z", "second-1"));

        HistoricalOhlcSeries result = evidenceRepository.findAsOf(
                firstMarket, OhlcInterval.FIFTEEN_MINUTES,
                Instant.parse("2026-10-15T11:00:00Z"), 1,
                HistoricalOhlcEvidencePolicy.STRICT_OBSERVED);

        assertThat(result.observations()).extracting(HistoricalOhlcEvidence::sourceId)
                .containsExactly("first-2");
    }

    @Test
    void exactFingerprintIsIdempotent() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        HistoricalOhlcEvidence value = evidence(
                marketId, Instant.parse("2026-10-15T09:45:00Z"), "100",
                "2026-10-15T10:00:02Z", "same-observation");
        HistoricalOhlcEvidence retry = new HistoricalOhlcEvidence(
                UUID.randomUUID(), value.marketId(), value.interval(), value.openTime(), value.closeTime(),
                value.open(), value.high(), value.low(), value.close(), value.volume(), value.vwap(),
                value.tradeCount(), value.closed(), value.synthetic(), value.provider(), value.providerSymbol(),
                value.sourceId(), value.sourceUpdatedAt(), value.fetchedAt(), value.capturedAt(), value.knownAt(),
                value.origin(), value.normalizationVersion(), value.observationFingerprint());

        evidenceRepository.append(value);
        evidenceRepository.append(retry);

        HistoricalOhlcSeries result = evidenceRepository.findAsOf(
                marketId, OhlcInterval.FIFTEEN_MINUTES,
                Instant.parse("2026-10-15T11:00:00Z"), 10,
                HistoricalOhlcEvidencePolicy.STRICT_OBSERVED);

        assertThat(result.observations()).extracting(HistoricalOhlcEvidence::sourceId)
                .containsExactly("same-observation");
        assertThat(result.observations().getFirst().observationId()).isEqualTo(value.observationId());
    }

    @Test
    void strictObservedReadsExcludeHistoricalBackfill() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        Instant knownAt = Instant.parse("2026-10-15T10:00:02Z");
        evidenceRepository.append(evidence(marketId, Instant.parse("2026-10-15T09:45:00Z"),
                "100", knownAt.toString(), "captured"));
        evidenceRepository.append(evidence(marketId, Instant.parse("2026-10-15T10:00:00Z"),
                "101", knownAt.toString(), "backfill", HistoricalOhlcEvidenceOrigin.HISTORICAL_BACKFILL));

        HistoricalOhlcSeries result = evidenceRepository.findAsOf(
                marketId, OhlcInterval.FIFTEEN_MINUTES, Instant.parse("2026-10-15T11:00:00Z"),
                10, HistoricalOhlcEvidencePolicy.STRICT_OBSERVED);

        assertThat(result.observations()).extracting(HistoricalOhlcEvidence::sourceId)
                .containsExactly("captured");
    }

    private HistoricalOhlcEvidence evidence(UUID marketId, Instant openTime, String open,
                                             String knownAtText, String sourceId) {
        return evidence(marketId, openTime, open, knownAtText, sourceId,
                HistoricalOhlcEvidenceOrigin.CAPTURED);
    }

    private HistoricalOhlcEvidence evidence(UUID marketId, Instant openTime, String open,
                                             String knownAtText, String sourceId,
                                             HistoricalOhlcEvidenceOrigin origin) {
        return new HistoricalOhlcEvidence(
                UUID.randomUUID(), marketId, OhlcInterval.FIFTEEN_MINUTES, openTime,
                openTime.plusSeconds(900), new BigDecimal(open), new BigDecimal("105"),
                new BigDecimal("95"), new BigDecimal("102"), new BigDecimal("10"),
                new BigDecimal("101"), 2, true, false, MarketProvider.KRAKEN, "BTC/USD", sourceId,
                null, Instant.parse(knownAtText), Instant.parse(knownAtText), Instant.parse(knownAtText),
                 origin,
                HistoricalOhlcEvidenceFingerprint.NORMALIZATION_VERSION,
                String.format("%064x", sourceId.hashCode()));
    }

    private Market market() {
        return market("BTC/USD", "BTC", "USD");
    }

    private Market market(String symbol, String baseAsset, String quoteAsset) {
        return Market.builder()
                .provider(MarketProvider.KRAKEN)
                .symbol(symbol)
                .providerSymbol(symbol)
                .baseAsset(baseAsset)
                .quoteAsset(quoteAsset)
                .productType(InstrumentProductType.SPOT)
                .marketConstraints(MarketConstraints.builder().build())
                .marketState(MarketState.builder().tradingStatus(TradingStatus.OPEN)
                        .tradable(true).lastUpdated(Instant.parse("2026-10-15T09:00:00Z")).build())
                .build();
    }
}
