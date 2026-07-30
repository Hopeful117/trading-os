package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.TradePlanRepository;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.util.*;
import java.util.concurrent.*;

public final class InMemoryTradePlanRepository implements TradePlanRepository {
    private final ConcurrentMap<TradePlanId, ConcurrentNavigableMap<Long, TradePlanEntity>>
            store = new ConcurrentHashMap<>();
    private final TradePlanMapper mapper = new TradePlanMapper();

    @Override public synchronized TradePlan append(TradePlan plan) {
        var history = store.computeIfAbsent(
                plan.id(), ignored -> new ConcurrentSkipListMap<>());
        long expected = history.isEmpty() ? 1 : history.lastKey() + 1;
        if (plan.version().value() != expected
                || history.putIfAbsent(plan.version().value(), mapper.toEntity(plan)) != null) {
            throw new IllegalStateException("TradePlan versions are append-only and sequential");
        }
        return plan;
    }
    @Override public Optional<TradePlan> find(TradePlanId id, TradePlanVersion version) {
        return Optional.ofNullable(store.get(id)).map(h -> h.get(version.value()))
                .map(mapper::toDomain);
    }
    @Override public Optional<TradePlan> findLatest(TradePlanId id) {
        return Optional.ofNullable(store.get(id)).filter(h -> !h.isEmpty())
                .map(h -> mapper.toDomain(h.lastEntry().getValue()));
    }
    @Override public Optional<TradePlan> findNext(
            TradePlanId id, TradePlanVersion version) {
        return Optional.ofNullable(store.get(id))
                .map(h -> h.higherEntry(version.value()))
                .map(Map.Entry::getValue).map(mapper::toDomain);
    }
    @Override public List<TradePlan> history(TradePlanId id) {
        return Optional.ofNullable(store.get(id)).stream()
                .flatMap(h -> h.values().stream()).map(mapper::toDomain).toList();
    }
}
