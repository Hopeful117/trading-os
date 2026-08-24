package com.hope.trading.trading_core.market_data.helper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketProviderTest {

    @Test
    void allEnumValuesAreAccessible() {
        assertThat(MarketProvider.values()).hasSize(5);
        assertThat(MarketProvider.valueOf("KRAKEN")).isEqualTo(MarketProvider.KRAKEN);
        assertThat(MarketProvider.valueOf("FTMO")).isEqualTo(MarketProvider.FTMO);
        assertThat(MarketProvider.valueOf("BINANCE")).isEqualTo(MarketProvider.BINANCE);
        assertThat(MarketProvider.valueOf("COINBASE")).isEqualTo(MarketProvider.COINBASE);
        assertThat(MarketProvider.valueOf("BYBIT")).isEqualTo(MarketProvider.BYBIT);
    }
}
