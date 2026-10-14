package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesTickerDto;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.TickerEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class KrakenFuturesTickerMapperTest {
    @Test
    void mapsFreshPerpetualBidAskAndSourceTimeIntoGenericTickerEvent() {
        Instant sourceTime = Instant.parse("2026-10-13T22:03:13.205770134Z");
        KrakenFuturesTickerDto dto = new KrakenFuturesTickerDto();
        dto.setSymbol("PF_XBTUSD");
        dto.setBid(new BigDecimal("83367"));
        dto.setAsk(new BigDecimal("83368"));
        dto.setLast(new BigDecimal("83369"));
        dto.setLastTime(sourceTime);

        Market market = Market.builder()
                .marketId(java.util.UUID.randomUUID())
                .provider(MarketProvider.KRAKEN)
                .symbol("BTC/USD")
                .providerSymbol("PF_XBTUSD")
                .productType(InstrumentProductType.LINEAR_PERPETUAL)
                .build();

        TickerEvent event = new KrakenFuturesTickerMapper().toEvent(dto, market);

        assertThat(event.symbol()).isEqualTo("BTC/USD");
        assertThat(event.bid()).isEqualByComparingTo("83367");
        assertThat(event.ask()).isEqualByComparingTo("83368");
        assertThat(event.last()).isEqualByComparingTo("83369");
        assertThat(event.occurredAt()).isEqualTo(sourceTime);
    }
}
