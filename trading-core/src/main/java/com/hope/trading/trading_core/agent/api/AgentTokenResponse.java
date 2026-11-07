package com.hope.trading.trading_core.agent.api;

import java.time.Instant;

public record AgentTokenResponse(String accessToken, String tokenType, Instant expiresAt, long expiresIn) {
}
