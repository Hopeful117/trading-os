package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.kraken.dto.ticker.KrakenTickerData;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.TickerEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class KrakenTickerMapper {
    public TickerEvent toEvent(
            KrakenTickerData data,
            Market market
    ) {
        return new TickerEvent(
                market.getMarketId(),
                market.getProvider(),
                market.getSymbol(),
                data.getBid(),
                data.getAsk(),
                data.getLast(),
                data.getVolume(),
                Instant.now()
        );
    }
}
