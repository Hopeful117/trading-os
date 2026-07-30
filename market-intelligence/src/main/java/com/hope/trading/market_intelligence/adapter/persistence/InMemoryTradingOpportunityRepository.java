package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class InMemoryTradingOpportunityRepository implements TradingOpportunityRepository {
    private final ConcurrentMap<OpportunityId, ConcurrentNavigableMap<Long, TradingOpportunityEntity>>
            store = new ConcurrentHashMap<>();
    private final TradingOpportunityMapper mapper = new TradingOpportunityMapper();

    @Override
    public synchronized TradingOpportunity append(TradingOpportunity opportunity) {
        ConcurrentNavigableMap<Long, TradingOpportunityEntity> history =
                store.computeIfAbsent(opportunity.id(), ignored -> new ConcurrentSkipListMap<>());
        long expected = history.isEmpty() ? 1 : history.lastKey() + 1;
        if (opportunity.version().value() != expected) {
            throw new IllegalStateException("Opportunity versions must be appended sequentially");
        }
        if (history.putIfAbsent(opportunity.version().value(), mapper.toEntity(opportunity)) != null) {
            throw new IllegalStateException("Opportunity version already exists");
        }
        return opportunity;
    }

    @Override public Optional<TradingOpportunity> find(
            OpportunityId id, OpportunityVersion version) {
        return Optional.ofNullable(store.get(id))
                .map(history -> history.get(version.value())).map(mapper::toDomain);
    }
    @Override public Optional<TradingOpportunity> findLatest(OpportunityId id) {
        return Optional.ofNullable(store.get(id)).filter(history -> !history.isEmpty())
                .map(history -> mapper.toDomain(history.lastEntry().getValue()));
    }
    @Override public List<TradingOpportunity> findActive() {
        return findAllLatest().stream()
                .filter(item -> item.status() == OpportunityStatus.ACTIVE).toList();
    }
    @Override public List<TradingOpportunity> findHistory(OpportunityId id) {
        return Optional.ofNullable(store.get(id)).stream()
                .flatMap(history -> history.values().stream()).map(mapper::toDomain).toList();
    }
    @Override public List<TradingOpportunity> findEquivalentCandidates(
            String instrument, OpportunityDirection direction, String scenario,
            String timeframe, Instant evaluatedAfter) {
        return findAllLatest().stream()
                .filter(item -> item.instrument().equalsIgnoreCase(instrument))
                .filter(item -> item.direction() == direction)
                .filter(item -> item.scenario().equalsIgnoreCase(scenario))
                .filter(item -> item.timeframe().equalsIgnoreCase(timeframe))
                .filter(item -> !item.evaluatedAt().isBefore(evaluatedAfter))
                .toList();
    }
    @Override public List<TradingOpportunity> findAllLatest() {
        return store.values().stream().filter(history -> !history.isEmpty())
                .map(history -> mapper.toDomain(history.lastEntry().getValue()))
                .sorted(Comparator.comparing(TradingOpportunity::createdAt)
                        .thenComparing(item -> item.id().value()))
                .toList();
    }
}
