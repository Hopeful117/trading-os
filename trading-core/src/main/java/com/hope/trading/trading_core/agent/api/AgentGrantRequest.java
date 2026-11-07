package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record AgentGrantRequest(
        @NotNull AgentPermission permission,
        @NotNull AgentResourceType resourceType,
        @NotNull UUID resourceId,
        Instant expiresAt) {
}
