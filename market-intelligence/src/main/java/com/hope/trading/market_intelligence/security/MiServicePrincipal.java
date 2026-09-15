package com.hope.trading.market_intelligence.security;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record MiServicePrincipal(String serviceName, String audience, UUID delegatedActor) {
    public UUID requireDelegatedActor() {
        if (delegatedActor == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "A delegated actor is required");
        }
        return delegatedActor;
    }

    public UUID requireMatchingActor(UUID compatibilityActor) {
        UUID actor = requireDelegatedActor();
        if (compatibilityActor == null || !actor.equals(compatibilityActor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Delegated actor does not match request actor");
        }
        return actor;
    }
}
