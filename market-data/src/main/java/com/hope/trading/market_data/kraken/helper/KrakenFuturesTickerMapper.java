package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesTickerDto;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.TickerEvent;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class KrakenFuturesTickerMapper {
    public TickerEvent toEvent(KrakenFuturesTickerDto dto, Market market) {
        return new TickerEvent(
                market.getMarketId(),
                market.getProvider(),
                market.getSymbol(),
                dto.getBid(),
                dto.getAsk(),
                dto.getLast(),
                null,
                Objects.requireNonNull(dto.getLastTime(), "Futures ticker source timestamp is required")
        );
    }
}
