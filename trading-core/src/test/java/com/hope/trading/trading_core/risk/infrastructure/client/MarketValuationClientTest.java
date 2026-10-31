package com.hope.trading.trading_core.risk.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotRequest;
import com.hope.trading.trading_core.risk.application.port.MarketValuationPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MarketValuationClientTest {
    @Test
    void preservesHardenedFreshnessAndConversionProvenance() {
        MarketValuationFeignClient feign = mock(MarketValuationFeignClient.class);
        UUID marketId = UUID.randomUUID();
        UUID conversionMarketId = UUID.randomUUID();
        Instant effectiveAt = Instant.parse("2026-08-01T11:59:50Z");
        Instant capturedAt = Instant.parse("2026-08-01T11:59:51Z");
        var source = new ValuationTransport.Source(UUID.randomUUID(), marketId, "KRAKEN", "BTCEUR",
                "BID", new BigDecimal("100"), effectiveAt, capturedAt, "PT10S");
        var leg = new ValuationTransport.ConversionLeg("EUR", "USD", new BigDecimal("1.2"), source);
        var fact = new ValuationTransport.Fact("INSTRUMENT", "position", marketId, null,
                "CONSERVATIVE_SELL", new BigDecimal("120"), "AVAILABLE", source, List.of(leg));
        when(feign.markets()).thenReturn(List.of(
                new CatalogueMarket(marketId, "KRAKEN", "BTCEUR", "BTC", "EUR"),
                new CatalogueMarket(conversionMarketId, "KRAKEN", "EURUSD", "EUR", "USD")));
        when(feign.value(org.mockito.ArgumentMatchers.any())).thenReturn(new ValuationTransport(UUID.randomUUID(),
                9, "USD", Instant.parse("2026-08-01T12:00:00Z"), capturedAt,
                "conservative-v2", "PT30S", "COMPLETE", List.of(fact)));
        MarketValuationClient client = new MarketValuationClient(feign, new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC));

        MarketValuationPort.Snapshot result = client.value("USD", Instant.parse("2026-08-01T12:00:00Z"),
                List.of(new MarketValuationPort.Instrument("position", "BTCEUR",
                        MarketValuationPort.PriceUse.CONSERVATIVE_SELL)), List.of());

        assertThat(result.maxObservationAge()).isEqualTo("PT30S");
        assertThat(result.facts().getFirst().quoteToReportingRate()).isEqualByComparingTo("1.2");
        assertThat(result.facts().getFirst().sourceProvenance())
                .contains("observationAge", "PT10S", "capturedAt");
        assertThat(result.sourcePayload()).contains("maxObservationAge", "PT30S");
        verify(feign).refresh(new MarketPriceSnapshotRequest(List.of(marketId, conversionMarketId),
                Duration.ofMinutes(5), Duration.ofSeconds(18), "REUSE_OR_ACQUIRE"));
    }

    @Test
    void refreshesConversionMarketForAssetOnlyValuation() {
        MarketValuationFeignClient feign = mock(MarketValuationFeignClient.class);
        UUID conversionMarketId = UUID.randomUUID();
        Instant valuationAt = Instant.parse("2026-08-01T12:00:00Z");
        when(feign.markets()).thenReturn(List.of(
                new CatalogueMarket(conversionMarketId, "KRAKEN", "EURUSD", "EUR", "USD")));
        when(feign.value(org.mockito.ArgumentMatchers.any())).thenReturn(new ValuationTransport(
                UUID.randomUUID(), 1, "USD", valuationAt, valuationAt,
                "conservative-v2", "PT30S", "COMPLETE", List.of()));
        MarketValuationClient client = new MarketValuationClient(feign, new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(valuationAt, ZoneOffset.UTC));

        client.value("USD", valuationAt, List.of(),
                List.of(new MarketValuationPort.Asset("EUR", "EUR")));

        verify(feign).refresh(new MarketPriceSnapshotRequest(List.of(conversionMarketId),
                Duration.ofMinutes(5), Duration.ofSeconds(18), "REUSE_OR_ACQUIRE"));
    }

    @Test
    void refreshesBothMarketsForTwoHopAssetValuation() {
        MarketValuationFeignClient feign = mock(MarketValuationFeignClient.class);
        UUID assetUsdMarketId = UUID.randomUUID();
        UUID eurUsdMarketId = UUID.randomUUID();
        Instant valuationAt = Instant.parse("2026-08-01T12:00:00Z");
        when(feign.markets()).thenReturn(List.of(
                new CatalogueMarket(assetUsdMarketId, "KRAKEN", "USDGUSD", "USDG", "USD"),
                new CatalogueMarket(eurUsdMarketId, "KRAKEN", "EURUSD", "EUR", "USD")));
        when(feign.value(org.mockito.ArgumentMatchers.any())).thenReturn(new ValuationTransport(
                UUID.randomUUID(), 1, "EUR", valuationAt, valuationAt,
                "conservative-v3", "PT5M", "COMPLETE", List.of()));
        MarketValuationClient client = new MarketValuationClient(feign, new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(valuationAt, ZoneOffset.UTC));

        client.value("EUR", valuationAt, List.of(),
                List.of(new MarketValuationPort.Asset("USDG", "USDG")));

        verify(feign).refresh(new MarketPriceSnapshotRequest(List.of(assetUsdMarketId, eurUsdMarketId),
                Duration.ofMinutes(5), Duration.ofSeconds(18), "REUSE_OR_ACQUIRE"));
    }

    @Test
    void sendsExecutionTimeConfirmationPolicyToMarketData() {
        MarketValuationFeignClient feign = mock(MarketValuationFeignClient.class);
        UUID marketId = UUID.randomUUID();
        Instant valuationAt = Instant.parse("2026-08-01T12:00:00Z");
        when(feign.markets()).thenReturn(List.of(
                new CatalogueMarket(marketId, "KRAKEN", "PF_ETHUSD", "ETH", "USD")));
        when(feign.value(org.mockito.ArgumentMatchers.any())).thenReturn(new ValuationTransport(
                UUID.randomUUID(), 1, "USD", valuationAt, valuationAt,
                "conservative-v3", "PT30S", "COMPLETE", List.of()));
        MarketValuationClient client = new MarketValuationClient(feign, new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(valuationAt, ZoneOffset.UTC));

        client.value("USD", valuationAt,
                List.of(new MarketValuationPort.Instrument("proposed", "PF_ETHUSD",
                        MarketValuationPort.PriceUse.CONSERVATIVE_BUY)), List.of(),
                MarketValuationPort.FreshnessRequirement.t1());

        verify(feign).refresh(new MarketPriceSnapshotRequest(List.of(marketId),
                Duration.ofSeconds(30), Duration.ofSeconds(18), "CONFIRM_OR_ACQUIRE"));
    }

    @Test
    void readOnlyValuationDoesNotRefreshOrPersistMarketFacts() {
        MarketValuationFeignClient feign = mock(MarketValuationFeignClient.class);
        UUID marketId = UUID.randomUUID();
        Instant valuationAt = Instant.parse("2026-08-01T12:00:00Z");
        var source = new ValuationTransport.Source(UUID.randomUUID(), marketId, "KRAKEN", "EURUSD",
                "BID", new BigDecimal("1.10"), valuationAt.minusSeconds(5), valuationAt.minusSeconds(4), "PT5S");
        var fact = new ValuationTransport.Fact("INSTRUMENT", "position", marketId, null,
                "CONSERVATIVE_SELL", new BigDecimal("1.10"), "AVAILABLE", source, List.of());
        when(feign.markets()).thenReturn(List.of(
                new CatalogueMarket(marketId, "KRAKEN", "EURUSD", "EUR", "USD")));
        when(feign.facts(org.mockito.ArgumentMatchers.any())).thenReturn(new ValuationTransport(
                UUID.randomUUID(), 1, "USD", valuationAt, valuationAt,
                "conservative-v3", "PT5M", "COMPLETE", List.of(fact)));
        MarketValuationClient client = new MarketValuationClient(feign, new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(valuationAt, ZoneOffset.UTC));

        MarketValuationPort.Snapshot result = client.valueReadOnly("USD", valuationAt,
                List.of(new MarketValuationPort.Instrument("position", "EURUSD",
                        MarketValuationPort.PriceUse.CONSERVATIVE_SELL)), List.of(),
                MarketValuationPort.FreshnessRequirement.t0());

        assertThat(result.complete()).isTrue();
        assertThat(result.facts().getFirst().sourceEffectiveAt()).isEqualTo(valuationAt.minusSeconds(5));
        verify(feign).facts(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(feign, org.mockito.Mockito.never())
                .refresh(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void preservesEveryReadOnlyMultiHopConversionLegAndLatestTimestamps() {
        MarketValuationFeignClient feign = mock(MarketValuationFeignClient.class);
        UUID marketId = UUID.randomUUID();
        Instant valuationAt = Instant.parse("2026-08-01T12:00:00Z");
        var firstSource = new ValuationTransport.Source(UUID.randomUUID(), UUID.randomUUID(), "KRAKEN",
                "USDCHF", "BID", new BigDecimal("0.90"), valuationAt.minusSeconds(20),
                valuationAt.minusSeconds(19), "PT20S");
        var secondSource = new ValuationTransport.Source(UUID.randomUUID(), UUID.randomUUID(), "KRAKEN",
                "CHFEUR", "BID", new BigDecimal("0.80"), valuationAt.minusSeconds(10),
                valuationAt.minusSeconds(9), "PT10S");
        var fact = new ValuationTransport.Fact("INSTRUMENT", "position", marketId, null,
                "CONSERVATIVE_SELL", new BigDecimal("0.72"), "AVAILABLE",
                new ValuationTransport.Source(UUID.randomUUID(), marketId, "KRAKEN", "EURUSD", "BID",
                        new BigDecimal("1.10"), valuationAt.minusSeconds(5), valuationAt.minusSeconds(4), "PT5S"),
                List.of(new ValuationTransport.ConversionLeg("USD", "CHF", new BigDecimal("0.90"), firstSource),
                        new ValuationTransport.ConversionLeg("CHF", "EUR", new BigDecimal("0.80"), secondSource)));
        when(feign.markets()).thenReturn(List.of(
                new CatalogueMarket(marketId, "KRAKEN", "EURUSD", "EUR", "USD")));
        when(feign.facts(org.mockito.ArgumentMatchers.any())).thenReturn(new ValuationTransport(
                UUID.randomUUID(), 1, "EUR", valuationAt, valuationAt,
                "conservative-v3", "PT5M", "COMPLETE", List.of(fact)));
        MarketValuationClient client = new MarketValuationClient(feign, new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(valuationAt, ZoneOffset.UTC));

        MarketValuationPort.Snapshot result = client.valueReadOnly("EUR", valuationAt,
                List.of(new MarketValuationPort.Instrument("position", "EURUSD",
                        MarketValuationPort.PriceUse.CONSERVATIVE_SELL)), List.of(),
                MarketValuationPort.FreshnessRequirement.t0());

        assertThat(result.facts().getFirst().conversionLegs()).hasSize(2);
        assertThat(result.facts().getFirst().conversionEffectiveAt()).isEqualTo(valuationAt.minusSeconds(10));
        assertThat(result.facts().getFirst().conversionCapturedAt()).isEqualTo(valuationAt.minusSeconds(9));
        assertThat(result.facts().getFirst().quoteToReportingRate()).isEqualByComparingTo("0.72");
    }
}
