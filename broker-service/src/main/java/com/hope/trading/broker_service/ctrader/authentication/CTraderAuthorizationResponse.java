package com.hope.trading.broker_service.ctrader.authentication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CTraderAuthorizationResponse(
        UUID attemptId,
        String authorizationUrl,
        CTraderAuthorizationAttemptStatus status,
        List<String> discoveredAccountIds,
        String selectedExternalAccountId,
        Instant expiresAt,
        String safeMessage) {
    public CTraderAuthorizationResponse {
        discoveredAccountIds = List.copyOf(discoveredAccountIds);
    }
}
