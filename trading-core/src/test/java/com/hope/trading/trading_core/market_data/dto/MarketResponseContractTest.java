package com.hope.trading.trading_core.market_data.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketResponseContractTest {
    @Test
    void deserializesCanonicalCfdProductTypeFromMarketData() throws Exception {
        String response = "{\"marketId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\","
                + "\"provider\":\"CTRADER\",\"symbol\":\"EUR/USD\","
                + "\"productType\":\"CFD\"}";

        MarketResponse market = new ObjectMapper().readValue(response, MarketResponse.class);

        assertThat(market.getProductType()).isEqualTo(InstrumentProductType.CFD);
        assertThat(market.getProvider()).isEqualTo(com.hope.trading.trading_core.market_data.helper.MarketProvider.CTRADER);
    }
}
