package com.hope.trading.broker_service.connection.application;

import java.util.UUID;

public interface CredentialCommandRateLimiter {
    void check(UUID ownerId, UUID brokerAccountId);
}
