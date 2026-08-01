package com.hope.trading.trading_core.risk.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.risk.application.port.MarketValuationPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MarketValuationClientTest {
    @Test
    void preservesHardenedFreshnessAndConversionProvenance() {
        MarketValuationFeignClient feign = mock(MarketValuationFeignClient.class);
        UUID marketId = UUID.randomUUID();
        Instant effectiveAt = Instant.parse("2026-08-01T11:59:50Z");
        Instant capturedAt = Instant.parse("2026-08-01T11:59:51Z");
        var source = new ValuationTransport.Source(UUID.randomUUID(), marketId, "KRAKEN", "BTCEUR",
                "BID", new BigDecimal("100"), effectiveAt, capturedAt, "PT10S");
        var leg = new ValuationTransport.ConversionLeg("EUR", "USD", new BigDecimal("1.2"), source);
        var fact = new ValuationTransport.Fact("INSTRUMENT", "position", marketId, null,
                "CONSERVATIVE_SELL", new BigDecimal("120"), "AVAILABLE", source, List.of(leg));
        when(feign.markets()).thenReturn(List.of(new CatalogueMarket(marketId, "KRAKEN", "BTCEUR", "BTC", "EUR")));
        when(feign.value(org.mockito.ArgumentMatchers.any())).thenReturn(new ValuationTransport(UUID.randomUUID(),
                9, "USD", Instant.parse("2026-08-01T12:00:00Z"), capturedAt,
                "conservative-v2", "PT30S", "COMPLETE", List.of(fact)));
        MarketValuationClient client = new MarketValuationClient(feign, new ObjectMapper().findAndRegisterModules());

        MarketValuationPort.Snapshot result = client.value("USD", Instant.parse("2026-08-01T12:00:00Z"),
                List.of(new MarketValuationPort.Instrument("position", "BTCEUR",
                        MarketValuationPort.PriceUse.CONSERVATIVE_SELL)), List.of());

        assertThat(result.maxObservationAge()).isEqualTo("PT30S");
        assertThat(result.facts().getFirst().quoteToReportingRate()).isEqualByComparingTo("1.2");
        assertThat(result.facts().getFirst().sourceProvenance())
                .contains("observationAge", "PT10S", "capturedAt");
        assertThat(result.sourcePayload()).contains("maxObservationAge", "PT30S");
    }
}
