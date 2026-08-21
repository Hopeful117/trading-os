package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.kraken.dto.ticker.KrakenRestTickerData;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.TickerEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Component
public class KrakenRestTickerMapper {
    public TickerEvent toEvent(
            KrakenRestTickerData data,
            Market market,
            Instant occurredAt
    ) {
        return new TickerEvent(
                market.getMarketId(),
                market.getProvider(),
                market.getSymbol(),
                decimal(first(data.getBid())),
                decimal(first(data.getAsk())),
                decimal(first(data.getLastTrade())),
                decimal(first(data.getVolume())),
                occurredAt
        );
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private BigDecimal decimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }
}
