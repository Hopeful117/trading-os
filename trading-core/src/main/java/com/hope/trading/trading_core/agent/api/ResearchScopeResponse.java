package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.model.ResearchScope;
import com.hope.trading.trading_core.agent.model.ResearchScopeStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ResearchScopeResponse(UUID id, UUID ownerUserId, Set<UUID> marketIds,
                                    ResearchScopeStatus status, Instant createdAt,
                                    Instant expiresAt, Instant revokedAt, long scopeVersion) {
    public static ResearchScopeResponse from(ResearchScope value) {
        return new ResearchScopeResponse(value.id(), value.ownerUserId(), value.marketIds(), value.status(),
                value.createdAt(), value.expiresAt(), value.revokedAt(), value.scopeVersion());
    }
}
