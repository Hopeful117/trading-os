package com.hope.trading.market_intelligence.application.port;

import java.util.Optional;
import java.util.UUID;

public interface ManualTradePlanIdempotencyRepository {
    Optional<Entry> findByActorIdAndKey(UUID actorId, String key);

    Entry save(Entry entry);

    record Entry(UUID actorId, String key, String fingerprint, UUID tradePlanId, long tradePlanVersion) { }
}
