package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.kraken.dto.KrakenMessageType;
import com.hope.trading.market_data.kraken.dto.orderbook.KrakenOrderBookData;
import com.hope.trading.market_data.kraken.dto.orderbook.KrakenOrderBookLevel;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.OrderBookDelta;
import com.hope.trading.market_data.model.OrderBookDeltaType;
import com.hope.trading.market_data.model.OrderBookLevel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KrakenOrderBookMapper {

    public OrderBookDelta toDelta(
            KrakenOrderBookData data,
            KrakenMessageType messageType,
            Market market,
            int depth
    ) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "Kraken order-book data is required"
            );
        }
        if (messageType == null) {
            throw new IllegalArgumentException(
                    "Kraken order-book message type is required"
            );
        }
        if (data.timestamp() == null) {
            throw new IllegalArgumentException(
                    "Kraken order-book timestamp is required"
            );
        }

        return new OrderBookDelta(
                market.getMarketId(),
                market.getProvider(),
                market.getSymbol(),
                depth,
                messageType == KrakenMessageType.SNAPSHOT
                        ? OrderBookDeltaType.SNAPSHOT
                        : OrderBookDeltaType.UPDATE,
                mapLevels(data.bids()),
                mapLevels(data.asks()),
                data.timestamp(),
                data.checksum()
        );
    }

    private List<OrderBookLevel> mapLevels(
            List<KrakenOrderBookLevel> levels
    ) {
        if (levels == null) {
            return List.of();
        }

        return levels.stream()
                .map(level ->
                        new OrderBookLevel(
                                level.price(),
                                level.quantity()
                        )
                )
                .toList();
    }
}
