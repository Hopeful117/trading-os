package com.hope.trading.broker_service.connection.api;

import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.credential.domain.CredentialValidationOutcome;

import java.time.Instant;
import java.util.Set;

public record CredentialValidationResponse(
        CredentialValidationOutcome outcome,
        BrokerConnectionStatus connectionStatus,
        Set<BrokerPermission> missingPermissions,
        Instant validatedAt,
        String safeMessage
) {
    public CredentialValidationResponse {
        missingPermissions = Set.copyOf(missingPermissions);
    }
}
