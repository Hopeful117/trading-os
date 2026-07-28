package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.kraken.dto.trade.KrakenTradeData;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.TradeEvent;
import com.hope.trading.market_data.model.TradeSide;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class KrakenTradeMapper {

    public TradeEvent toEvent(
            KrakenTradeData data,
            Market market
    ) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "Kraken trade data is required"
            );
        }
        if (market == null) {
            throw new IllegalArgumentException(
                    "Trade market is required"
            );
        }
        if (data.tradeId() == null) {
            throw new IllegalArgumentException(
                    "Kraken trade id is required"
            );
        }
        if (data.price() == null || data.price().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Kraken trade price must be positive"
            );
        }
        if (data.quantity() == null
                || data.quantity().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Kraken trade quantity must be positive"
            );
        }
        if (data.timestamp() == null) {
            throw new IllegalArgumentException(
                    "Kraken trade timestamp is required"
            );
        }

        return new TradeEvent(
                market.getMarketId(),
                market.getProvider(),
                market.getSymbol(),
                data.tradeId().toString(),
                mapSide(data.side()),
                data.price(),
                data.quantity(),
                data.price().multiply(data.quantity()),
                data.timestamp()
        );
    }

    /**
     * Kraken reports the taker side. BUY consumes ask liquidity and SELL
     * consumes bid liquidity.
     */
    private TradeSide mapSide(String side) {
        if (side == null || side.isBlank()) {
            throw new IllegalArgumentException(
                    "Kraken trade side is required"
            );
        }

        return switch (side.trim().toLowerCase(Locale.ROOT)) {
            case "buy" -> TradeSide.BUY;
            case "sell" -> TradeSide.SELL;
            default -> throw new IllegalArgumentException(
                    "Unsupported Kraken trade side: " + side
            );
        };
    }
}
