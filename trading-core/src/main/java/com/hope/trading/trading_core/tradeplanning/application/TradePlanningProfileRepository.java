package com.hope.trading.trading_core.tradeplanning.application;

import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TradePlanningProfileRepository {
    Optional<TradePlanningProfile> find(UUID id, long version);
    Optional<TradePlanningProfile> findLatest(UUID id);
    Optional<TradePlanningProfile> findAssigned(UUID accountId);
    TradePlanningProfile append(TradePlanningProfile profile);
    void assign(UUID accountId, UUID profileId, long profileVersion, UUID actorId, Instant assignedAt);
}
