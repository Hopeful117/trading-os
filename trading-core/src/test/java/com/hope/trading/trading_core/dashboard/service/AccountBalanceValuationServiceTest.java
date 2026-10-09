package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.risk.application.port.MarketValuationPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountBalanceValuationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    private final MarketValuationPort market = mock(MarketValuationPort.class);
    private final AccountBalanceValuationService service = new AccountBalanceValuationService(
            market, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aggregatesBaseAndNonBaseAssetsInReportingCurrency() {
        when(market.value(eq("USD"), eq(NOW), any(), any())).thenReturn(snapshot(true,
                new MarketValuationPort.Fact("ASSET", "USD", null, "USD", null,
                        BigDecimal.ONE, null, BigDecimal.ONE, "AVAILABLE", null),
                new MarketValuationPort.Fact("ASSET", "EUR", null, "EUR", null,
                        new BigDecimal("1.10"), null, new BigDecimal("1.10"), "AVAILABLE", null)));

        AccountValuationResult result = service.value("USD", Map.of(
                "USD", new BigDecimal("1000"), "eur", new BigDecimal("100")));

        assertThat(result.value()).isEqualByComparingTo("1110");
        assertThat(result.status()).isEqualTo("COMPLETE");
    }

    @Test
    void canonicalizesKrakenBitcoinBalanceAlias() {
        when(market.value(eq("USD"), eq(NOW), any(), any())).thenReturn(snapshot(true,
                new MarketValuationPort.Fact("ASSET", "xxbt", null, "XBT", null,
                        new BigDecimal("100"), null, null, "AVAILABLE", null)));

        AccountValuationResult result = service.value("USD", Map.of("XXBT", new BigDecimal("2")));

        assertThat(result.value()).isEqualByComparingTo("200");
        assertThat(result.status()).isEqualTo("COMPLETE");
    }

    @Test
    void canonicalizesGenericKrakenLegacyAssetPrefixes() {
        when(market.value(eq("USD"), eq(NOW), any(), any())).thenReturn(snapshot(true,
                new MarketValuationPort.Fact("ASSET", "xeth", null, "ETH", null,
                        new BigDecimal("2000"), null, null, "AVAILABLE", null),
                new MarketValuationPort.Fact("ASSET", "zusd", null, "USD", null,
                        BigDecimal.ONE, null, null, "AVAILABLE", null)));

        AccountValuationResult result = service.value("USD", Map.of(
                "XETH", BigDecimal.ONE, "ZUSD", new BigDecimal("10")));

        assertThat(result.value()).isEqualByComparingTo("2010");
        assertThat(result.status()).isEqualTo("COMPLETE");
    }

    @Test
    void failsClosedWhenOneAssetHasNoAvailableFact() {
        when(market.value(eq("USD"), eq(NOW), any(), any())).thenReturn(snapshot(false,
                new MarketValuationPort.Fact("ASSET", "USD", null, "USD", null,
                        BigDecimal.ONE, null, BigDecimal.ONE, "AVAILABLE", null),
                new MarketValuationPort.Fact("ASSET", "EUR", null, "EUR", null,
                        null, null, null, "CONVERSION_UNAVAILABLE", null)));

        AccountValuationResult result = service.value("USD", Map.of(
                "USD", new BigDecimal("1000"), "EUR", new BigDecimal("100")));

        assertThat(result.value()).isNull();
        assertThat(result.status()).isEqualTo("INCOMPLETE");
    }

    @Test
    void failsClosedWhenBalanceAmountIsMissing() {
        when(market.value(eq("USD"), eq(NOW), any(), any())).thenReturn(snapshot(true));

        Map<String, BigDecimal> balances = new HashMap<>();
        balances.put("USD", null);
        AccountValuationResult result = service.value("USD", balances);

        assertThat(result.value()).isNull();
        assertThat(result.status()).isEqualTo("INCOMPLETE");
    }

    private MarketValuationPort.Snapshot snapshot(boolean complete, MarketValuationPort.Fact... facts) {
        return new MarketValuationPort.Snapshot(UUID.randomUUID(), 1, "USD", NOW, NOW,
                "CONSERVATIVE_MULTI_HOP_FX_NO_LOOKAHEAD_V3", "PT5M", complete, List.of(facts), "{}");
    }
}
