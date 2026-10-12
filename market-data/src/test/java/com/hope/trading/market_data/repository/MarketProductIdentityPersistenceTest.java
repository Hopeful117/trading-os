package com.hope.trading.market_data.repository;

import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.helper.TradingStatus;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketConstraints;
import com.hope.trading.market_data.model.MarketState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MarketProductIdentityPersistenceTest {
    @Autowired
    private MarketRepository markets;

    @Test
    void providerAndSymbolMayBeSharedAcrossProductTypes() {
        markets.saveAndFlush(market(InstrumentProductType.SPOT));
        markets.saveAndFlush(market(InstrumentProductType.LINEAR_PERPETUAL));

        assertThat(markets.findByProviderAndSymbolAndProductType(
                MarketProvider.KRAKEN, "BTC/USD", InstrumentProductType.SPOT)).isPresent();
        assertThat(markets.findByProviderAndSymbolAndProductType(
                MarketProvider.KRAKEN, "BTC/USD", InstrumentProductType.LINEAR_PERPETUAL)).isPresent();
    }

    private Market market(InstrumentProductType productType) {
        return Market.builder()
                .provider(MarketProvider.KRAKEN)
                .symbol("BTC/USD")
                .baseAsset("BTC")
                .quoteAsset("USD")
                .productType(productType)
                .marketConstraints(MarketConstraints.builder().build())
                .marketState(MarketState.builder()
                        .tradingStatus(TradingStatus.OPEN)
                        .tradable(true)
                        .lastUpdated(Instant.parse("2026-10-12T10:00:00Z"))
                        .build())
                .build();
    }
}
