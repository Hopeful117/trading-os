package com.hope.trading.trading_core.risk.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.hope.trading.trading_core.risk.application.port.MarketValuationPort;
import com.hope.trading.trading_core.risk.application.port.RequiredMarginPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequiredMarginCurrencyNormalizerTest {
    private static final Instant NOW = Instant.parse("2026-10-08T22:30:00Z");

    @Test
    void keepsMarginInReportingCurrency() {
        assertThat(RequiredMarginCurrencyNormalizer.normalize(
                margin("USD", new BigDecimal("100")), "USD", valuation("EUR", "1.2"), NOW))
                .contains(new BigDecimal("100"));
    }

    @Test
    void convertsMarginUsingCurrentAssetRate() {
        assertThat(RequiredMarginCurrencyNormalizer.normalize(
                margin("EUR", new BigDecimal("100")), "USD", valuation("EUR", "1.2"), NOW))
                .contains(new BigDecimal("120.0"));
    }

    @Test
    void failsClosedWhenConversionRateIsUnavailable() {
        assertThat(RequiredMarginCurrencyNormalizer.normalize(
                margin("EUR", new BigDecimal("100")), "USD", valuation("GBP", "1.2"), NOW))
                .isEmpty();
    }

    @Test
    void failsClosedWhenMarginFactIsFromTheFuture() {
        assertThat(RequiredMarginCurrencyNormalizer.normalize(
                new RequiredMarginPort.Fact(new BigDecimal("100"), "EUR", "source", 1,
                        NOW.plusSeconds(1)), "USD", valuation("EUR", "1.2"), NOW))
                .isEmpty();
    }

    @Test
    void failsClosedWhenValuationReportingCurrencyDoesNotMatch() {
        assertThat(RequiredMarginCurrencyNormalizer.normalize(
                margin("EUR", new BigDecimal("100")), "USD", valuation("EUR", "1.2", "EUR"), NOW))
                .isEmpty();
    }

    @Test
    void failsClosedWhenValuationIsStale() {
        assertThat(RequiredMarginCurrencyNormalizer.normalize(
                margin("EUR", new BigDecimal("100")), "USD",
                new MarketValuationPort.Snapshot(UUID.randomUUID(), 1, "USD",
                        NOW.minusSeconds(31), NOW, "policy", "PT30S", true,
                        List.of(new MarketValuationPort.Fact(
                                "ASSET", "eur", null, "EUR", null, new BigDecimal("1.2"),
                                null, null, "AVAILABLE", "source")), "payload"), NOW))
                .isEmpty();
    }

    @Test
    void failsClosedWhenValuationIsTooOldForMarginFact() {
        assertThat(RequiredMarginCurrencyNormalizer.normalize(
                margin("EUR", new BigDecimal("100")), "USD",
                new MarketValuationPort.Snapshot(UUID.randomUUID(), 1, "USD",
                        NOW.minusSeconds(31), NOW.minusSeconds(31), "policy", "PT30S", true,
                        List.of(new MarketValuationPort.Fact(
                                "ASSET", "eur", null, "EUR", null, new BigDecimal("1.2"),
                                null, null, "AVAILABLE", "source")), "payload"), NOW))
                .isEmpty();
    }

    @Test
    void failsClosedWhenConversionFactIsAmbiguous() {
        MarketValuationPort.Snapshot valuation = new MarketValuationPort.Snapshot(
                UUID.randomUUID(), 1, "USD", NOW, NOW, "policy", "PT30S", true,
                List.of(
                        new MarketValuationPort.Fact("ASSET", "eur-1", null, "EUR", null,
                                new BigDecimal("1.2"), null, null, "AVAILABLE", "source"),
                        new MarketValuationPort.Fact("ASSET", "eur-2", null, "EUR", null,
                                new BigDecimal("1.3"), null, null, "AVAILABLE", "source")),
                "payload");

        assertThat(RequiredMarginCurrencyNormalizer.normalize(
                margin("EUR", new BigDecimal("100")), "USD", valuation, NOW))
                .isEmpty();
    }

    private static RequiredMarginPort.Fact margin(String currency, BigDecimal amount) {
        return new RequiredMarginPort.Fact(amount, currency, "source", 1, NOW.minusSeconds(1));
    }

    private static MarketValuationPort.Snapshot valuation(String asset, String rate) {
        return valuation(asset, rate, "USD");
    }

    private static MarketValuationPort.Snapshot valuation(String asset, String rate, String reportingCurrency) {
        return new MarketValuationPort.Snapshot(UUID.randomUUID(), 1, reportingCurrency, NOW, NOW,
                "policy", "PT30S", true, List.of(new MarketValuationPort.Fact(
                "ASSET", asset.toLowerCase(), null, asset, null, new BigDecimal(rate),
                null, null, "AVAILABLE", "source")), "payload");
    }
}
