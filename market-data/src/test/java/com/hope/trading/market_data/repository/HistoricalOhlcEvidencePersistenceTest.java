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

    @Test
    void capturedRangeReportsCoverageAndMissingIntervals() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        evidenceRepository.append(evidence(marketId, Instant.parse("2026-10-15T10:00:00Z"),
                "100", "2026-10-15T10:15:02Z", "range-1"));
        evidenceRepository.append(evidence(marketId, Instant.parse("2026-10-15T10:15:00Z"),
                "101", "2026-10-15T10:30:02Z", "range-2"));
        evidenceRepository.append(evidence(marketId, Instant.parse("2026-10-15T10:45:00Z"),
                "103", "2026-10-15T11:00:02Z", "range-4"));

        HistoricalOhlcRange result = evidenceRepository.findRange(
                marketId, OhlcInterval.FIFTEEN_MINUTES,
                Instant.parse("2026-10-15T10:00:00Z"),
                Instant.parse("2026-10-15T11:00:00Z"),
                HistoricalOhlcEvidencePolicy.CAPTURED);

        assertThat(result.status()).isEqualTo(HistoricalOhlcRangeStatus.INCOMPLETE);
        assertThat(result.expectedObservationCount()).isEqualTo(4);
        assertThat(result.observedObservationCount()).isEqualTo(3);
        assertThat(result.coverageStart()).isEqualTo(Instant.parse("2026-10-15T10:00:00Z"));
        assertThat(result.coverageEnd()).isEqualTo(Instant.parse("2026-10-15T11:00:00Z"));
        assertThat(result.gaps()).extracting(HistoricalOhlcGap::openTime)
                .containsExactly(Instant.parse("2026-10-15T10:30:00Z"));
    }

    @Test
    void capturedRangeExcludesBackfillAndKeepsCorrectionSelectionDeterministic() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        evidenceRepository.append(evidence(marketId, Instant.parse("2026-10-15T10:00:00Z"),
                "100", "2026-10-15T10:15:02Z", "captured-old"));
        evidenceRepository.append(evidence(marketId, Instant.parse("2026-10-15T10:00:00Z"),
                "101", "2026-10-15T10:30:02Z", "captured-new"));
        evidenceRepository.append(evidence(marketId, Instant.parse("2026-10-15T10:15:00Z"),
                "102", "2026-10-15T10:30:03Z", "backfill",
                HistoricalOhlcEvidenceOrigin.HISTORICAL_BACKFILL));

        HistoricalOhlcRange result = evidenceRepository.findRange(
                marketId, OhlcInterval.FIFTEEN_MINUTES,
                Instant.parse("2026-10-15T10:00:00Z"),
                Instant.parse("2026-10-15T10:30:00Z"),
                HistoricalOhlcEvidencePolicy.CAPTURED);

        assertThat(result.observations()).extracting(HistoricalOhlcEvidence::sourceId)
                .containsExactly("captured-new");
        assertThat(result.gaps()).extracting(HistoricalOhlcGap::openTime)
                .containsExactly(Instant.parse("2026-10-15T10:15:00Z"));
    }

    @Test
    void capturedRangeIsolatesMarketAndIntervalAndFiltersInvalidEvidence() {
        UUID firstMarket = markets.saveAndFlush(market("BTC/USD", "BTC", "USD")).getMarketId();
        UUID secondMarket = markets.saveAndFlush(market("ETH/USD", "ETH", "USD")).getMarketId();
        evidenceRepository.append(evidence(firstMarket, Instant.parse("2026-10-15T10:00:00Z"),
                "100", "2026-10-15T10:15:02Z", "valid"));
        evidenceRepository.append(evidence(secondMarket, Instant.parse("2026-10-15T10:00:00Z"),
                "100", "2026-10-15T10:15:02Z", "other-market"));
        evidenceRepository.append(evidence(firstMarket, Instant.parse("2026-10-15T10:15:00Z"),
                "101", "2026-10-15T10:30:02Z", "synthetic", HistoricalOhlcEvidenceOrigin.CAPTURED,
                true, true));
        evidenceRepository.append(evidence(firstMarket, Instant.parse("2026-10-15T10:30:00Z"),
                "102", "2026-10-15T10:45:02Z", "open", HistoricalOhlcEvidenceOrigin.CAPTURED,
                false, false));
        evidenceRepository.append(evidence(firstMarket, OhlcInterval.ONE_HOUR,
                Instant.parse("2026-10-15T10:00:00Z"), "103", "2026-10-15T11:00:02Z", "other-interval"));

        HistoricalOhlcRange result = evidenceRepository.findRange(
                firstMarket, OhlcInterval.FIFTEEN_MINUTES,
                Instant.parse("2026-10-15T10:00:00Z"),
                Instant.parse("2026-10-15T11:00:00Z"),
                HistoricalOhlcEvidencePolicy.CAPTURED);

        assertThat(result.observations()).extracting(HistoricalOhlcEvidence::sourceId)
                .containsExactly("valid");
        assertThat(result.gaps()).hasSize(3);
    }

    private HistoricalOhlcEvidence evidence(UUID marketId, Instant openTime, String open,
                                             String knownAtText, String sourceId) {
        return evidence(marketId, openTime, open, knownAtText, sourceId,
                HistoricalOhlcEvidenceOrigin.CAPTURED);
    }

    private HistoricalOhlcEvidence evidence(UUID marketId, Instant openTime, String open,
                                              String knownAtText, String sourceId,
                                              HistoricalOhlcEvidenceOrigin origin) {
        return evidence(marketId, openTime, open, knownAtText, sourceId, origin, true, false);
    }

    private HistoricalOhlcEvidence evidence(UUID marketId, Instant openTime, String open,
                                             String knownAtText, String sourceId,
                                             HistoricalOhlcEvidenceOrigin origin,
                                             boolean closed, boolean synthetic) {
        return new HistoricalOhlcEvidence(
                UUID.randomUUID(), marketId, OhlcInterval.FIFTEEN_MINUTES, openTime,
                openTime.plusSeconds(900), new BigDecimal(open), new BigDecimal("105"),
                new BigDecimal("95"), new BigDecimal("102"), new BigDecimal("10"),
                new BigDecimal("101"), 2, closed, synthetic, MarketProvider.KRAKEN, "BTC/USD", sourceId,
                null, Instant.parse(knownAtText), Instant.parse(knownAtText), Instant.parse(knownAtText),
                 origin,
                HistoricalOhlcEvidenceFingerprint.NORMALIZATION_VERSION,
                String.format("%064x", sourceId.hashCode()));
    }

    private HistoricalOhlcEvidence evidence(UUID marketId, OhlcInterval interval, Instant openTime,
                                             String open, String knownAtText, String sourceId) {
        Instant knownAt = Instant.parse(knownAtText);
        return new HistoricalOhlcEvidence(
                UUID.randomUUID(), marketId, interval, openTime,
                openTime.plus(interval.getDuration()), new BigDecimal(open), new BigDecimal("105"),
                new BigDecimal("95"), new BigDecimal("102"), new BigDecimal("10"),
                new BigDecimal("101"), 2, true, false, MarketProvider.KRAKEN, "BTC/USD", sourceId,
                null, knownAt, knownAt, knownAt,
                HistoricalOhlcEvidenceOrigin.CAPTURED,
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
