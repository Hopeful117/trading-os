package com.hope.trading.broker_service.connection.api;

import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record BrokerConnectionResponse(
        UUID brokerAccountId,
        BrokerProviderId provider,
        BrokerConnectionStatus connectionStatus,
        String externalAccountId,
        String apiKeyHint,
        Set<BrokerPermission> detectedPermissions,
        Instant lastValidatedAt
) {
    public BrokerConnectionResponse {
        detectedPermissions = Set.copyOf(detectedPermissions);
    }
}
