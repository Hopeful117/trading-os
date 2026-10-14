package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.helper.MarketStateBuilder;
import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesInstrumentDto;
import com.hope.trading.market_data.model.Market;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class KrakenFuturesMarketMapperTest {
    private final KrakenFuturesMarketMapper mapper =
            new KrakenFuturesMarketMapper(new MarketStateBuilder());

    @Test
    void mapsFlexibleFuturesToCanonicalLinearPerpetual() {
        KrakenFuturesInstrumentDto dto = instrument("PF_XBTUSD", "flexible_futures");

        Market market = mapper.toDomain(dto, true);

        assertThat(market.getProviderSymbol()).isEqualTo("PF_XBTUSD");
        assertThat(market.getSymbol()).isEqualTo("BTC/USD");
        assertThat(market.getProductType()).isEqualTo(InstrumentProductType.LINEAR_PERPETUAL);
        assertThat(market.getMarketState().isTradable()).isTrue();
        assertThat(market.getMarketConstraints().tickSize()).isEqualByComparingTo("1");
    }

    @Test
    void preservesUnavailableStatusWithoutCallingItTradable() {
        Market market = mapper.toDomain(instrument("PF_XBTUSD", "flexible_futures"), false);

        assertThat(market.getMarketState().isTradable()).isFalse();
        assertThat(market.getMarketState().getTradingStatus().name()).isEqualTo("CLOSED");
    }

    private KrakenFuturesInstrumentDto instrument(String symbol, String type) {
        KrakenFuturesInstrumentDto dto = new KrakenFuturesInstrumentDto();
        dto.setSymbol(symbol);
        dto.setType(type);
        dto.setPair("BTC:USD");
        dto.setBase("BTC");
        dto.setQuote("USD");
        dto.setTickSize(new BigDecimal("1"));
        dto.setIsExpired(false);
        return dto;
    }
}
