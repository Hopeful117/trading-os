package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.ManualTradePlanIdempotencyRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryManualTradePlanIdempotencyRepository
        implements ManualTradePlanIdempotencyRepository {
    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<Entry> findByActorIdAndKey(UUID actorId, String key) {
        return Optional.ofNullable(entries.get(index(actorId, key)));
    }

    @Override
    public synchronized Entry save(Entry entry) {
        String index = index(entry.actorId(), entry.key());
        Entry previous = entries.putIfAbsent(index, entry);
        if (previous != null && !previous.equals(entry)) {
            throw new IllegalStateException("Manual Trade Plan idempotency key is already used");
        }
        return entry;
    }

    private String index(UUID actorId, String key) {
        return actorId + ":" + key;
    }
}
