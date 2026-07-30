package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.TradingContextRepository;
import com.hope.trading.market_intelligence.domain.tradeplan.TradingContext;
import java.util.*;
import java.util.concurrent.*;

public final class InMemoryTradingContextRepository implements TradingContextRepository {
    private final ConcurrentMap<UUID, ConcurrentNavigableMap<Long, TradingContext>> store =
            new ConcurrentHashMap<>();
    @Override public TradingContext saveSnapshot(TradingContext context) {
        TradingContext existing = store.computeIfAbsent(
                context.id(), ignored -> new ConcurrentSkipListMap<>())
                .putIfAbsent(context.version(), context);
        if (existing != null) throw new IllegalStateException("Context snapshot already exists");
        return context;
    }
    @Override public Optional<TradingContext> find(UUID id, long version) {
        return Optional.ofNullable(store.get(id)).map(values -> values.get(version));
    }
    @Override public Optional<TradingContext> findLatest(UUID id) {
        return Optional.ofNullable(store.get(id)).filter(values -> !values.isEmpty())
                .map(values -> values.lastEntry().getValue());
    }
}
