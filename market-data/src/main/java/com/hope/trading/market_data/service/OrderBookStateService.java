package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.OrderBookDelta;
import com.hope.trading.market_data.model.OrderBookDeltaType;
import com.hope.trading.market_data.model.OrderBookKey;
import com.hope.trading.market_data.model.OrderBookLevel;
import com.hope.trading.market_data.model.OrderBookSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
public class OrderBookStateService {
    private static final BigDecimal NEUTRAL_IMBALANCE =
            new BigDecimal("0.5");
    private static final int IMBALANCE_SCALE = 10;

    private final ConcurrentMap<OrderBookKey, OrderBookState> states =
            new ConcurrentHashMap<>();

    public OrderBookSnapshot initialize(OrderBookDelta delta) {
        validateDelta(delta, OrderBookDeltaType.SNAPSHOT);

        OrderBookState state = new OrderBookState();
        applyLevels(state.bids, delta.bids());
        applyLevels(state.asks, delta.asks());

        OrderBookKey key =
                new OrderBookKey(delta.marketId(), delta.depth());
        states.put(key, state);

        return toSnapshot(delta, state);
    }

    public Optional<OrderBookSnapshot> update(OrderBookDelta delta) {
        validateDelta(delta, OrderBookDeltaType.UPDATE);

        OrderBookKey key =
                new OrderBookKey(delta.marketId(), delta.depth());
        OrderBookState state = states.get(key);

        if (state == null) {
            log.warn(
                    "Ignoring order-book update before snapshot marketId={} depth={}",
                    delta.marketId(),
                    delta.depth()
            );
            return Optional.empty();
        }

        synchronized (state) {
            validateLevels(delta.bids());
            validateLevels(delta.asks());
            applyLevels(state.bids, delta.bids());
            applyLevels(state.asks, delta.asks());

            return Optional.of(toSnapshot(delta, state));
        }
    }

    public void clear(OrderBookKey key) {
        states.remove(key);
    }

    public boolean hasState(OrderBookKey key) {
        return states.containsKey(key);
    }

    private void validateDelta(
            OrderBookDelta delta,
            OrderBookDeltaType expectedType
    ) {
        if (delta == null) {
            throw new IllegalArgumentException(
                    "Order-book delta is required"
            );
        }
        if (delta.marketId() == null) {
            throw new IllegalArgumentException(
                    "Order-book market id is required"
            );
        }
        if (delta.provider() == null) {
            throw new IllegalArgumentException(
                    "Order-book provider is required"
            );
        }
        if (delta.symbol() == null || delta.symbol().isBlank()) {
            throw new IllegalArgumentException(
                    "Order-book symbol is required"
            );
        }
        if (delta.depth() < 1) {
            throw new IllegalArgumentException(
                    "Order-book depth must be positive"
            );
        }
        if (delta.type() != expectedType) {
            throw new IllegalArgumentException(
                    "Expected order-book delta type " + expectedType
            );
        }
        if (delta.occurredAt() == null) {
            throw new IllegalArgumentException(
                    "Order-book timestamp is required"
            );
        }

        validateLevels(delta.bids());
        validateLevels(delta.asks());
    }

    private void validateLevels(List<OrderBookLevel> levels) {
        for (OrderBookLevel level : levels) {
            if (level == null
                    || level.price() == null
                    || level.price().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Order-book level price must be positive"
                );
            }
            if (level.quantity() == null
                    || level.quantity().signum() < 0) {
                throw new IllegalArgumentException(
                        "Order-book level quantity must not be negative"
                );
            }
        }
    }

    private void applyLevels(
            NavigableMap<BigDecimal, BigDecimal> side,
            List<OrderBookLevel> levels
    ) {
        levels.forEach(level -> {
            if (level.quantity().signum() == 0) {
                side.remove(level.price());
            } else {
                side.put(level.price(), level.quantity());
            }
        });
    }

    private OrderBookSnapshot toSnapshot(
            OrderBookDelta delta,
            OrderBookState state
    ) {
        List<OrderBookLevel> bids =
                limitedLevels(state.bids, delta.depth());
        List<OrderBookLevel> asks =
                limitedLevels(state.asks, delta.depth());

        BigDecimal bestBid = firstPrice(bids);
        BigDecimal bestAsk = firstPrice(asks);
        BigDecimal spread = bestBid == null || bestAsk == null
                ? null
                : bestAsk.subtract(bestBid);
        BigDecimal bidVolume = totalVolume(bids);
        BigDecimal askVolume = totalVolume(asks);

        return new OrderBookSnapshot(
                delta.marketId(),
                delta.provider(),
                delta.symbol(),
                delta.depth(),
                bids,
                asks,
                bestBid,
                bestAsk,
                spread,
                bidVolume,
                askVolume,
                imbalance(bidVolume, askVolume),
                delta.occurredAt()
        );
    }

    private List<OrderBookLevel> limitedLevels(
            NavigableMap<BigDecimal, BigDecimal> side,
            int depth
    ) {
        List<OrderBookLevel> levels = new ArrayList<>(depth);

        side.entrySet()
                .stream()
                .limit(depth)
                .map(entry ->
                        new OrderBookLevel(
                                entry.getKey(),
                                entry.getValue()
                        )
                )
                .forEach(levels::add);

        return List.copyOf(levels);
    }

    private BigDecimal firstPrice(List<OrderBookLevel> levels) {
        return levels.isEmpty() ? null : levels.getFirst().price();
    }

    private BigDecimal totalVolume(List<OrderBookLevel> levels) {
        return levels.stream()
                .map(OrderBookLevel::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal imbalance(
            BigDecimal bidVolume,
            BigDecimal askVolume
    ) {
        BigDecimal total = bidVolume.add(askVolume);

        if (total.signum() == 0) {
            return NEUTRAL_IMBALANCE;
        }

        return bidVolume.divide(
                total,
                IMBALANCE_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private static final class OrderBookState {
        private final NavigableMap<BigDecimal, BigDecimal> bids =
                new TreeMap<>(Comparator.reverseOrder());
        private final NavigableMap<BigDecimal, BigDecimal> asks =
                new TreeMap<>();
    }
}
