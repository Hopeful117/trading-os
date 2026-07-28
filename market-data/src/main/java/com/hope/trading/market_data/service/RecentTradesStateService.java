package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.RecentTradesSnapshot;
import com.hope.trading.market_data.model.TradeEvent;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RecentTradesStateService {
    static final int MAX_RECENT_TRADES = 100;

    private static final Comparator<TradeEvent> MOST_RECENT_FIRST =
            Comparator.comparing(TradeEvent::occurredAt).reversed();

    private final ConcurrentMap<UUID, RecentTradesBuffer> buffers =
            new ConcurrentHashMap<>();
    private final Clock clock;

    public RecentTradesStateService() {
        this(Clock.systemUTC());
    }

    RecentTradesStateService(Clock clock) {
        this.clock = clock;
    }

    public RecentTradesSnapshot add(
            TradeEvent trade
    ) {
        return addBatch(List.of(trade));
    }

    public RecentTradesSnapshot addBatch(
            List<TradeEvent> trades
    ) {
        if (trades == null || trades.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one trade is required"
            );
        }

        trades.forEach(this::validateTrade);

        UUID marketId = trades.getFirst().marketId();

        if (trades.stream().anyMatch(trade ->
                !trade.marketId().equals(marketId)
        )) {
            throw new IllegalArgumentException(
                    "A recent-trades batch must contain one market"
            );
        }

        RecentTradesBuffer buffer = buffers.computeIfAbsent(
                marketId,
                ignored -> new RecentTradesBuffer()
        );

        synchronized (buffer) {
            Map<String, TradeEvent> uniqueTrades =
                    new LinkedHashMap<>();
            buffer.trades.forEach(trade ->
                    uniqueTrades.put(trade.tradeId(), trade)
            );
            trades.forEach(trade ->
                    uniqueTrades.putIfAbsent(trade.tradeId(), trade)
            );

            List<TradeEvent> orderedTrades =
                    new ArrayList<>(uniqueTrades.values());
            orderedTrades.sort(MOST_RECENT_FIRST);

            if (orderedTrades.size() > MAX_RECENT_TRADES) {
                orderedTrades = new ArrayList<>(
                        orderedTrades.subList(
                                0,
                                MAX_RECENT_TRADES
                        )
                );
            }

            buffer.trades = orderedTrades;

            TradeEvent reference = orderedTrades.getFirst();
            return new RecentTradesSnapshot(
                    reference.marketId(),
                    reference.provider(),
                    reference.symbol(),
                    orderedTrades,
                    clock.instant()
            );
        }
    }

    public void clear(UUID marketId) {
        buffers.remove(marketId);
    }

    public boolean hasState(UUID marketId) {
        return buffers.containsKey(marketId);
    }

    private void validateTrade(TradeEvent trade) {
        if (trade == null) {
            throw new IllegalArgumentException("Trade is required");
        }
        if (trade.marketId() == null) {
            throw new IllegalArgumentException(
                    "Trade market id is required"
            );
        }
        if (trade.provider() == null) {
            throw new IllegalArgumentException(
                    "Trade provider is required"
            );
        }
        if (trade.symbol() == null || trade.symbol().isBlank()) {
            throw new IllegalArgumentException(
                    "Trade symbol is required"
            );
        }
        if (trade.tradeId() == null || trade.tradeId().isBlank()) {
            throw new IllegalArgumentException(
                    "Trade id is required"
            );
        }
        if (trade.side() == null) {
            throw new IllegalArgumentException(
                    "Trade side is required"
            );
        }
        if (trade.price() == null || trade.price().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Trade price must be positive"
            );
        }
        if (trade.quantity() == null
                || trade.quantity().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Trade quantity must be positive"
            );
        }
        if (trade.notional() == null
                || trade.notional().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Trade notional must be positive"
            );
        }
        if (trade.occurredAt() == null) {
            throw new IllegalArgumentException(
                    "Trade timestamp is required"
            );
        }
    }

    private static final class RecentTradesBuffer {
        private List<TradeEvent> trades = new ArrayList<>();
    }
}
