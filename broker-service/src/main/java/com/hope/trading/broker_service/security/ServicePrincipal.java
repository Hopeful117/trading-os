package com.hope.trading.broker_service.security;

import java.util.UUID;

public record ServicePrincipal(String serviceName, String audience, UUID actorId,
                               UUID delegatedAgentId, UUID delegatedCredentialId,
                               Integer delegatedCredentialVersion) {
    public ServicePrincipal(String serviceName, String audience, UUID actorId) {
        this(serviceName, audience, actorId, null, null, null);
    }
}
