package com.hope.trading.broker_service.credential.domain;

import com.hope.trading.broker_service.connection.domain.BrokerPermission;

import java.time.Instant;
import java.util.Set;

public record CredentialValidationResult(
        CredentialValidationOutcome outcome,
        String externalAccountId,
        Set<BrokerPermission> detectedPermissions,
        Set<BrokerPermission> missingPermissions,
        Instant validatedAt,
        SafeProviderDiagnostics diagnostics
) {
    public CredentialValidationResult {
        detectedPermissions = Set.copyOf(detectedPermissions);
        missingPermissions = Set.copyOf(missingPermissions);
    }
}
