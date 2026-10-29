package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HistoricalOhlcContextContributorTest {
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final Instant CUTOFF = Instant.parse("2026-10-15T12:00:00Z");

    @Test
    void reportsMissingHistoryWhenMarketDataReturnsNoCandles() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findOhlc(MARKET_ID, "FIFTEEN_MINUTES", 200)).thenReturn(List.of());

        ContextSection section = contributor(client).contribute(request(CUTOFF));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.MISSING);
        assertThat(section.message()).isEqualTo("OHLC history is unavailable");
    }

    @Test
    void reportsIncompleteWhenAllCandlesAreNewerThanCutoff() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findOhlc(MARKET_ID, "FIFTEEN_MINUTES", 200)).thenReturn(List.of(
                candle(CUTOFF.plusSeconds(60), CUTOFF.plusSeconds(120))));

        ContextSection section = contributor(client).contribute(request(CUTOFF));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.INCOMPLETE);
        assertThat(section.payload()).isNull();
        assertThat(section.provenance().sourceOccurredAt()).isNull();
        assertThat(section.message()).contains("evaluation cut-off");
    }

    @Test
    void keepsAcceptedCandlesAndUsesLatestAvailableTimes() {
        MarketDataClient client = mock(MarketDataClient.class);
        Instant acceptedAt = CUTOFF.minusSeconds(60);
        when(client.findOhlc(MARKET_ID, "FIFTEEN_MINUTES", 200)).thenReturn(List.of(
                candle(acceptedAt, CUTOFF.minusSeconds(30)),
                candle(CUTOFF.plusSeconds(60), CUTOFF.plusSeconds(120))));

        ContextSection section = contributor(client).contribute(request(CUTOFF));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.AVAILABLE);
        HistoricalOhlcContext context = (HistoricalOhlcContext) section.payload();
        assertThat(context.candles()).hasSize(1);
        assertThat(context.marketId()).isEqualTo(MARKET_ID);
        assertThat(section.provenance().sourceOccurredAt()).isEqualTo(acceptedAt);
        assertThat(section.provenance().fetchedAt()).isEqualTo(CUTOFF.minusSeconds(30));
    }

    @Test
    void reportsIncompleteWhenTemporalMetadataIsMissing() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findOhlc(MARKET_ID, "FIFTEEN_MINUTES", 200)).thenReturn(List.of(
                candle(null, null)));

        ContextSection section = contributor(client).contribute(request(CUTOFF));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.INCOMPLETE);
        assertThat(section.payload()).isNull();
        assertThat(section.provenance().sourceOccurredAt()).isNull();
        assertThat(section.provenance().fetchedAt()).isEqualTo(CUTOFF);
    }

    @Test
    void acceptsFutureOccurredAtWhenNoEvaluationCutoffExists() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findOhlc(MARKET_ID, "FIFTEEN_MINUTES", 200)).thenReturn(List.of(
                candle(CUTOFF.plusSeconds(60), CUTOFF.plusSeconds(120))));

        ContextSection section = contributor(client).contribute(request(null));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.STALE);
        assertThat(((HistoricalOhlcContext) section.payload()).candles()).hasSize(1);
    }

    @Test
    void excludesCandleFetchedAfterEvaluationCutoff() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findOhlc(MARKET_ID, "FIFTEEN_MINUTES", 200)).thenReturn(List.of(
                candle(CUTOFF.minusSeconds(60), CUTOFF.plusSeconds(60))));

        ContextSection section = contributor(client).contribute(request(CUTOFF));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.INCOMPLETE);
        assertThat(section.payload()).isNull();
    }

    @Test
    void usesCandleCloseTimeForFreshnessWhenSourceWasUpdatedAtAnotherTime() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findOhlc(MARKET_ID, "FIFTEEN_MINUTES", 200)).thenReturn(List.of(
                new OhlcResponse(
                        MARKET_ID, "KRAKEN", "BTC/EUR", "FIFTEEN_MINUTES",
                        Instant.parse("2026-10-15T11:45:00Z"),
                        Instant.parse("2026-10-15T12:00:00Z"),
                        new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("95"),
                        new BigDecimal("102"), BigDecimal.TEN, new BigDecimal("101"), 2,
                        true, Instant.parse("2026-10-15T10:00:00Z"), false, "ohlc",
                        Instant.parse("2026-10-15T11:59:30Z"))));

        ContextSection section = contributor(client).contribute(request(CUTOFF));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.AVAILABLE);
        assertThat(section.provenance().sourceOccurredAt())
                .isEqualTo(Instant.parse("2026-10-15T10:00:00Z"));
    }

    @Test
    void ordersAcceptedCandlesAndRetainsSourceReferences() {
        MarketDataClient client = mock(MarketDataClient.class);
        OhlcResponse later = candle(CUTOFF.minusSeconds(60), CUTOFF.minusSeconds(30));
        OhlcResponse earlier = new OhlcResponse(
                MARKET_ID, "KRAKEN", "BTC/EUR", "FIFTEEN_MINUTES",
                CUTOFF.minusSeconds(1800), CUTOFF.minusSeconds(900),
                new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("95"),
                new BigDecimal("102"), BigDecimal.TEN, new BigDecimal("101"), 2,
                true, CUTOFF.minusSeconds(1800), false, "earlier", CUTOFF.minusSeconds(30));
        later = new OhlcResponse(
                later.marketId(), later.provider(), later.symbol(), later.interval(),
                later.openTime(), later.closeTime(), later.open(), later.high(), later.low(),
                later.close(), later.volume(), later.vwap(), later.trades(), later.closed(),
                later.occurredAt(), later.synthetic(), "later", later.fetchedAt());
        when(client.findOhlc(MARKET_ID, "FIFTEEN_MINUTES", 200)).thenReturn(List.of(later, earlier));

        ContextSection section = contributor(client).contribute(request(CUTOFF));
        HistoricalOhlcContext context = (HistoricalOhlcContext) section.payload();

        assertThat(context.candles().getFirst().openTime()).isEqualTo(earlier.openTime());
        assertThat(section.provenance().sourceReference()).isEqualTo("earlier,later");
        assertThat(section.provenance().sourceVersion()).isEqualTo("KRAKEN");
    }

    private HistoricalOhlcContextContributor contributor(MarketDataClient client) {
        return new HistoricalOhlcContextContributor(
                client, new MarketDataSectionFactory(java.time.Duration.ofSeconds(30)));
    }

    private IntelligenceAnalysisRequest request(Instant cutoff) {
        return new IntelligenceAnalysisRequest(
                UUID.randomUUID(), MARKET_ID, AnalysisExecutionMode.ACTIVE, "context",
                null, null, null, cutoff);
    }

    private OhlcResponse candle(Instant occurredAt, Instant fetchedAt) {
        Instant openTime = occurredAt == null ? CUTOFF : occurredAt.minusSeconds(900);
        return new OhlcResponse(
                MARKET_ID, "KRAKEN", "BTC/EUR", "FIFTEEN_MINUTES", openTime,
                occurredAt, new BigDecimal("100"), new BigDecimal("105"),
                new BigDecimal("95"), new BigDecimal("102"), BigDecimal.TEN,
                new BigDecimal("101"), 2, true, occurredAt, false, "ohlc", fetchedAt);
    }
}
