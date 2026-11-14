package com.hope.trading.market_intelligence.application.port;

import java.time.Instant;
import java.util.UUID;

public interface PassiveDispatchCapacityRepository {
    boolean acquire(UUID leaseId, UUID scanMarketId, int capacity, Instant expiresAt, Instant now);

    boolean release(UUID leaseId, Instant now);

    int recoverExpired(Instant now);
}
