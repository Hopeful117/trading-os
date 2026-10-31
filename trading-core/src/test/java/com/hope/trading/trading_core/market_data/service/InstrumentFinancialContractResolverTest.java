package com.hope.trading.trading_core.market_data.service;

import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstrumentFinancialContractResolverTest {
    private final MarketDataClient marketData = mock(MarketDataClient.class);
    private final InstrumentFinancialContractResolver resolver =
            new InstrumentFinancialContractResolver(marketData, new InstrumentFinancialContractMapper());

    @Test
    void resolvesCompleteCfdMetadataFromMarketData() {
        when(marketData.findAll()).thenReturn(List.of(MarketResponse.builder()
                .marketId(UUID.randomUUID()).symbol("EUR/USD").productType(InstrumentProductType.CFD)
                .baseAsset("EUR").quoteAsset("USD").quantityUnit("UNDERLYING_UNITS")
                .contractMultiplier(new BigDecimal("1"))
                .contractSourceId("market-data:eurusd").contractSourceVersion(3L)
                .contractObservedAt(Instant.parse("2026-10-31T12:00:00Z"))
                .marketConstraints(com.hope.trading.trading_core.market_data.dto.MarketConstraints.builder()
                        .minimumOrderSize(new BigDecimal("0.01"))
                        .orderStepSize(new BigDecimal("0.01"))
                        .quantityPrecision(2).pricePrecision(5).build())
                .build()));

        var contract = resolver.resolve("EUR/USD");

        assertThat(contract.productType()).isEqualTo(InstrumentProductType.CFD);
        assertThat(contract.contractMultiplier()).isEqualByComparingTo("1");
        assertThat(contract.provenance().sourceVersion()).isEqualTo(3L);
    }

    @Test
    void failsClosedWhenCfdMetadataIsIncomplete() {
        when(marketData.findAll()).thenReturn(List.of(MarketResponse.builder()
                .symbol("EUR/USD").productType(InstrumentProductType.CFD)
                .baseAsset("EUR").quoteAsset("USD").quantityUnit("UNDERLYING_UNITS")
                .marketConstraints(com.hope.trading.trading_core.market_data.dto.MarketConstraints.builder()
                        .minimumOrderSize(new BigDecimal("0.01"))
                        .orderStepSize(new BigDecimal("0.01"))
                        .quantityPrecision(2).pricePrecision(5).build())
                .build()));

        assertThatThrownBy(() -> resolver.resolve("EUR/USD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("complete financial contract metadata");
    }

    @Test
    void failsClosedWhenMarketIdentityIsUnavailable() {
        when(marketData.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> resolver.resolveIfCfd("EUR/USD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("financial contract market is unavailable");
    }

    @Test
    void returnsNoContractForAConfirmedNonCfdMarket() {
        when(marketData.findAll()).thenReturn(List.of(MarketResponse.builder()
                .symbol("XBT/USD").productType(InstrumentProductType.LINEAR_PERPETUAL)
                .baseAsset("XBT").quoteAsset("USD")
                .marketConstraints(com.hope.trading.trading_core.market_data.dto.MarketConstraints.builder()
                        .minimumOrderSize(new BigDecimal("1"))
                        .orderStepSize(new BigDecimal("1"))
                        .quantityPrecision(0).pricePrecision(2).build())
                .build()));

        assertThat(resolver.resolveIfCfd("XBT/USD")).isNull();
    }
}
