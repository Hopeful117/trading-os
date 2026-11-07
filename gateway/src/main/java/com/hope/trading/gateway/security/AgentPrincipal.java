package com.hope.trading.gateway.security;

import java.util.UUID;

public record AgentPrincipal(UUID agentId, UUID ownerUserId, UUID credentialId,
                             int credentialVersion, String tokenId) {
}
