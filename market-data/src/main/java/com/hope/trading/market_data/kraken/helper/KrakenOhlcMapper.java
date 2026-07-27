package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcEntry;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class KrakenOhlcMapper {
    public OhlcEvent toEvent(
            KrakenOhlcEntry entry,
            Market market,
            Instant messageTimestamp,
            boolean closed
    ) {
        OhlcInterval interval =
                OhlcInterval.fromMinutes(entry.interval());

        return new OhlcEvent(
                market.getMarketId(),
                market.getProvider(),
                market.getSymbol(),
                interval,
                entry.intervalBegin(),
                entry.timestamp(),
                entry.open(),
                entry.high(),
                entry.low(),
                entry.close(),
                entry.volume(),
                entry.vwap(),
                entry.trades(),
                closed,
                messageTimestamp
        );
    }
}
