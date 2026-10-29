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

        assertThat(section.status()).isEqualTo(ContextSectionStatus.AVAILABLE);
        assertThat(section.payload()).isInstanceOf(MarketIdentityContext.class);
        assertThat(section.provenance().sourceOccurredAt()).isEqualTo(updatedAt);
    }

    @Test
    void preservesStrictObservedEvidenceProvenance() {
        MarketDataClient client = mock(MarketDataClient.class);
        Instant updatedAt = Instant.parse("2026-10-15T12:00:00Z");
        Instant cutoff = Instant.parse("2026-10-15T12:00:01Z");
        when(client.findMarket(MARKET_ID)).thenReturn(new MarketResponse(
                MARKET_ID, "CTRADER", "EUR/USD", "EUR", "USD",
                new MarketResponse.MarketStateResponse("OPEN", true, null, updatedAt)));

        ContextSection section = contributor(client).contribute(new IntelligenceAnalysisRequest(
                UUID.randomUUID(), MARKET_ID, AnalysisExecutionMode.ACTIVE, "context",
                null, UUID.randomUUID(), UUID.randomUUID(), cutoff,
                EvidenceAcquisitionPolicy.STRICT_OBSERVED));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.AVAILABLE);
        assertThat(section.provenance().evidencePolicy())
                .isEqualTo(EvidenceAcquisitionPolicy.STRICT_OBSERVED);
        assertThat(section.provenance().evaluationCutoff()).isEqualTo(cutoff);
    }

    @Test
    void preservesExistingEvidenceMetadataWhenApplyingRequestProvenance() {
        MarketDataClient client = mock(MarketDataClient.class);
        MarketDataSectionFactory factory = mock(MarketDataSectionFactory.class);
        Instant occurredAt = Instant.parse("2026-10-15T12:00:00Z");
        Instant cutoff = Instant.parse("2026-10-15T12:00:01Z");
        String fingerprint = "evidence-fingerprint";
        ContextSection source = new ContextSection(
                ContextSectionType.MARKET_IDENTITY, ContextSectionStatus.AVAILABLE,
                ContextSensitivity.PUBLIC, null,
                new ContextProvenance("market-data", occurredAt, occurredAt,
                        "market-ref", "v1", "normalization-v1", null, fingerprint, 3, null), null);
        when(client.findMarket(MARKET_ID)).thenReturn(new MarketResponse(
                MARKET_ID, "CTRADER", "EUR/USD", "EUR", "USD",
                new MarketResponse.MarketStateResponse("OPEN", true, null, occurredAt)));
        when(factory.availableAt(any(), any(), any(), any(), any())).thenReturn(source);

        ContextSection section = new MarketIdentityContextContributor(client, factory).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), MARKET_ID, AnalysisExecutionMode.ACTIVE,
                        "context", null, null, null, cutoff, EvidenceAcquisitionPolicy.STRICT_OBSERVED));

        assertThat(section.provenance().evidenceFingerprint()).isEqualTo(fingerprint);
        assertThat(section.provenance().observationCount()).isEqualTo(3);
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
