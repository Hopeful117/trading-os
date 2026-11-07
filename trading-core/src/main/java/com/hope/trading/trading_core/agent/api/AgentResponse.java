package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.model.AgentStatus;
import com.hope.trading.trading_core.agent.model.AgentType;

import java.time.Instant;
import java.util.UUID;

public record AgentResponse(
        UUID id,
        UUID ownerUserId,
        String displayName,
        AgentType agentType,
        AgentStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
