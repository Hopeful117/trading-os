package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.model.AgentCredentialStatus;

import java.time.Instant;
import java.util.UUID;

public record AgentCredentialResponse(
        UUID credentialId,
        UUID agentId,
        String credential,
        String fingerprint,
        int credentialVersion,
        AgentCredentialStatus status,
        Instant createdAt,
        Instant expiresAt) {
}
