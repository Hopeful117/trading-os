package com.hope.trading.market_data.helper;

import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketConstraints;
import com.hope.trading.market_data.model.MarketState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MarketMapperFinancialContractTest {
    @Test
    void exposesProviderNeutralFinancialContractMetadata() {
        Market market = Market.builder()
                .symbol("EUR/USD")
                .baseAsset("EUR")
                .quoteAsset("USD")
                .productType(InstrumentProductType.CFD)
                .quantityUnit("UNDERLYING_UNITS")
                .contractMultiplier(new BigDecimal("1"))
                .contractSourceId("market-data:eurusd")
                .contractSourceVersion(3L)
                .contractObservedAt(Instant.parse("2026-10-31T12:00:00Z"))
                .marketConstraints(MarketConstraints.builder()
                        .minimumOrderSize(new BigDecimal("0.01"))
                        .orderStepSize(new BigDecimal("0.01"))
                        .quantityPrecision(2).pricePrecision(5).build())
                .build();

        var response = new MarketMapper().toDto(market);

        assertThat(response.getQuantityUnit()).isEqualTo("UNDERLYING_UNITS");
        assertThat(response.getContractMultiplier()).isEqualByComparingTo("1");
        assertThat(response.getContractSourceId()).isEqualTo("market-data:eurusd");
        assertThat(response.getContractSourceVersion()).isEqualTo(3L);
    }

    @Test
    void marksIncompleteIdentityAsNonTradableWithoutChangingTheStoredMarket() {
        Market market = Market.builder()
                .provider(MarketProvider.CTRADER)
                .symbol("EUR/USD")
                .productType(null)
                .providerSymbol(null)
                .marketState(MarketState.builder()
                        .tradingStatus(TradingStatus.OPEN)
                        .tradable(true)
                        .lastUpdated(Instant.parse("2026-11-07T12:00:00Z"))
                        .build())
                .build();

        var response = new MarketMapper().toDto(market);

        assertThat(response.isIdentityResolved()).isFalse();
        assertThat(response.getMarketState().isTradable()).isFalse();
        assertThat(response.getMarketState().getTradingStatus()).isEqualTo(TradingStatus.UNKNOWN);
        assertThat(market.getMarketState().isTradable()).isTrue();
    }
}
