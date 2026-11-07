package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.model.AgentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgentRequest(
        @NotBlank String displayName,
        @NotNull AgentType agentType) {
}
