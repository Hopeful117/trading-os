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
import java.util.Comparator;

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
                market.getProviderSymbol(),
                depth,
                messageType == KrakenMessageType.SNAPSHOT
                        ? OrderBookDeltaType.SNAPSHOT
                        : OrderBookDeltaType.UPDATE,
                mapLevels(data.bids(), true),
                mapLevels(data.asks(), false),
                data.timestamp(),
                data.checksum(),
                data.sequence()
        );
    }

    private List<OrderBookLevel> mapLevels(
            List<KrakenOrderBookLevel> levels,
            boolean descending
    ) {
        if (levels == null) {
            return List.of();
        }

        Comparator<OrderBookLevel> comparator = Comparator.comparing(OrderBookLevel::price);
        if (descending) {
            comparator = comparator.reversed();
        }
        return levels.stream()
                .map(level ->
                        new OrderBookLevel(
                                level.price(),
                                level.quantity()
                        )
                )
                .sorted(comparator)
                .toList();
    }
}
