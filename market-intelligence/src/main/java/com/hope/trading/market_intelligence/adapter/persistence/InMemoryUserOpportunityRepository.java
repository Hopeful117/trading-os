package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.UserOpportunityRepository;
import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.util.*;
import java.util.concurrent.*;

public final class InMemoryUserOpportunityRepository implements UserOpportunityRepository {
    private record Key(UUID userId, OpportunityId opportunityId) {}
    private final ConcurrentMap<Key, UserOpportunity> store = new ConcurrentHashMap<>();

    @Override public UserOpportunity save(UserOpportunity projection) {
        store.put(new Key(projection.userId(), projection.opportunityId()), projection);
        return projection;
    }
    @Override public Optional<UserOpportunity> find(
            UUID userId, OpportunityId opportunityId) {
        return Optional.ofNullable(store.get(new Key(userId, opportunityId)));
    }
    @Override public List<UserOpportunity> findByUser(UUID userId) {
        return store.values().stream().filter(item -> item.userId().equals(userId))
                .sorted(Comparator.comparing(item -> item.opportunityId().value())).toList();
    }
}
