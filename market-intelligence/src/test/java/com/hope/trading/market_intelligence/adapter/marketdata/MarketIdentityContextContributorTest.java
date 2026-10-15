package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MarketIdentityContextContributorTest {
    private static final UUID MARKET_ID = UUID.randomUUID();

    @Test
    void mapsTradableMarketIdentityAndFreshness() {
        MarketDataClient client = mock(MarketDataClient.class);
        Instant updatedAt = Instant.parse("2026-10-15T12:00:00Z");
        when(client.findMarket(MARKET_ID)).thenReturn(new MarketResponse(
                MARKET_ID, "KRAKEN", "BTC/EUR", "BTC", "EUR",
                new MarketResponse.MarketStateResponse("OPEN", true, null, updatedAt)));

        ContextSection section = contributor(client).contribute(request(null));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.INCOMPLETE);
        assertThat(section.payload()).isNull();
        assertThat(section.provenance().sourceOccurredAt()).isEqualTo(updatedAt);
    }

    @Test
    void mapsMarketWithoutStateAsNotTradable() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findMarket(MARKET_ID)).thenReturn(new MarketResponse(
                MARKET_ID, "KRAKEN", "BTC/EUR", "BTC", "EUR", null));

        ContextSection section = contributor(client).contribute(request(null));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.INCOMPLETE);
        assertThat(section.payload()).isNull();
        assertThat(section.provenance().sourceOccurredAt()).isNull();
    }

    @Test
    void mapsClosedMarketStateAsNotTradable() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findMarket(MARKET_ID)).thenReturn(new MarketResponse(
                MARKET_ID, "KRAKEN", "BTC/EUR", "BTC", "EUR",
                new MarketResponse.MarketStateResponse("CLOSED", false, "maintenance", null)));

        ContextSection section = contributor(client).contribute(request(null));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.INCOMPLETE);
        assertThat(section.payload()).isNull();
    }

    private MarketIdentityContextContributor contributor(MarketDataClient client) {
        return new MarketIdentityContextContributor(
                client, new MarketDataSectionFactory(Duration.ofSeconds(30)));
    }

    private IntelligenceAnalysisRequest request(Instant cutoff) {
        return new IntelligenceAnalysisRequest(
                UUID.randomUUID(), MARKET_ID, AnalysisExecutionMode.ACTIVE, "context",
                null, null, null, cutoff);
    }
}
