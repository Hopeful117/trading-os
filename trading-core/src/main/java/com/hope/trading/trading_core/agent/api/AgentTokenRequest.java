package com.hope.trading.trading_core.agent.api;

import jakarta.validation.constraints.NotBlank;

public record AgentTokenRequest(@NotBlank String credential) {
}
