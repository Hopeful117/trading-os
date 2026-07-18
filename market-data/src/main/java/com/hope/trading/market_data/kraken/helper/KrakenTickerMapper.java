package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.kraken.dto.KrakenTickerData;
import com.hope.trading.market_data.model.MarketDataEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class KrakenTickerMapper {
    public MarketDataEvent toEvent (KrakenTickerData data){
        return MarketDataEvent.builder()
                .provider(MarketProvider.KRAKEN)
                .symbol(data.getSymbol())
                .bid(data.getBid())
                .ask(data.getAsk())
                .last(data.getLast())
                .timestamp(Instant.now())
                .volume(data.getVolume())
                .build();
    }
}
