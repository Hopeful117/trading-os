package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.model.AgentGrantStatus;
import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;

import java.time.Instant;
import java.util.UUID;

public record AgentGrantResponse(
        UUID id,
        UUID agentId,
        AgentPermission permission,
        AgentResourceType resourceType,
        UUID resourceId,
        AgentGrantStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        String revocationReason,
        long grantVersion) {
}
