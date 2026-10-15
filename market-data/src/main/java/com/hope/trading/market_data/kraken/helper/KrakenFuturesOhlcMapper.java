package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesChartCandle;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class KrakenFuturesOhlcMapper {
    private final Clock clock;

    public KrakenFuturesOhlcMapper(Clock clock) {
        this.clock = clock;
    }

    public OhlcEvent toEvent(
            KrakenFuturesChartCandle candle,
            Market market,
            OhlcInterval interval,
            Instant fetchedAt
    ) {
        if (candle == null || candle.getTime() == null) {
            throw new IllegalArgumentException("Kraken Futures candle timestamp is required");
        }
        Instant openTime = Instant.ofEpochMilli(candle.getTime());
        Instant closeTime = openTime.plus(interval.getDuration());
        boolean closed = !closeTime.isAfter(clock.instant());
        return new OhlcEvent(
                market.getMarketId(), market.getProvider(), market.getSymbol(), interval,
                openTime, closeTime, candle.getOpen(), candle.getHigh(), candle.getLow(),
                candle.getClose(), candle.getVolume(), candle.getVwap(), candle.getTrades(),
                closed, fetchedAt, false,
                 OhlcEvent.defaultSourceId(market.getMarketId(), market.getProvider(),
                         market.getSymbol(), interval, openTime), fetchedAt, market.getProviderSymbol());
    }
}
