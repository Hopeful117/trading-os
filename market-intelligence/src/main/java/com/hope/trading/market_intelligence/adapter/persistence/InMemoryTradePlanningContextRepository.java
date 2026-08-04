package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.TradePlanningContextRepository;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanningContext;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public final class InMemoryTradePlanningContextRepository implements TradePlanningContextRepository {
    private final ConcurrentMap<UUID, ConcurrentNavigableMap<Long, TradePlanningContext>> store =
            new ConcurrentHashMap<>();

    @Override public void saveSnapshot(TradePlanningContext context) {
        TradePlanningContext existing = store.computeIfAbsent(
                context.id(), ignored -> new ConcurrentSkipListMap<>()).putIfAbsent(context.version(), context);
        if (existing != null) throw new IllegalStateException("Context snapshot already exists");
    }
    @Override public Optional<TradePlanningContext> find(UUID id, long version) {
        return Optional.ofNullable(store.get(id)).map(values -> values.get(version));
    }
    @Override public Optional<TradePlanningContext> findLatest(UUID id) {
        return Optional.ofNullable(store.get(id)).filter(values -> !values.isEmpty())
                .map(values -> values.lastEntry().getValue());
    }
}
