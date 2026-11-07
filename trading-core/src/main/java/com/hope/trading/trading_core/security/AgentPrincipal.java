package com.hope.trading.trading_core.security;

import java.util.UUID;

public record AgentPrincipal(UUID agentId, UUID ownerUserId, UUID credentialId,
                             int credentialVersion, String tokenId) {
}
