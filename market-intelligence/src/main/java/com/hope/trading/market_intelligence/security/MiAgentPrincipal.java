package com.hope.trading.market_intelligence.security;

import java.util.UUID;

public record MiAgentPrincipal(UUID agentId, UUID ownerUserId, UUID credentialId,
                               int credentialVersion, String tokenId) {
}
