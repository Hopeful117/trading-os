package com.hope.trading.broker_service.kraken.credential;

import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.credential.application.BrokerCredentialValidator;
import com.hope.trading.broker_service.credential.application.RequiredBrokerPermissionsPolicy;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.credential.domain.CredentialValidationOutcome;
import com.hope.trading.broker_service.credential.domain.CredentialValidationResult;
import com.hope.trading.broker_service.credential.domain.SafeProviderDiagnostics;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Component
public class KrakenCredentialValidator implements BrokerCredentialValidator {
    private final KrakenCredentialProbe probe;
    private final RequiredBrokerPermissionsPolicy permissionsPolicy;
    private final Clock clock;

    public KrakenCredentialValidator(KrakenCredentialProbe probe,
                                     RequiredBrokerPermissionsPolicy permissionsPolicy,
                                     Clock clock) {
        this.probe = probe;
        this.permissionsPolicy = permissionsPolicy;
        this.clock = clock;
    }

    @Override
    public BrokerProviderId provider() {
        return BrokerProviderId.KRAKEN;
    }

    @Override
    public CredentialValidationResult validate(CredentialMaterial credentials) {
        Instant validatedAt = clock.instant();
        KrakenCredentialProbe.ProbeResult result;
        try {
            result = probe.probe(credentials);
        } catch (IllegalArgumentException exception) {
            return response(CredentialValidationOutcome.UNSUPPORTED_CREDENTIAL_FORMAT, Set.of(), Set.of(),
                    validatedAt, "UNSUPPORTED_FORMAT", "Credential format is not supported");
        }
        Set<BrokerPermission> required = permissionsPolicy.requiredForReadOnly(provider());
        Set<BrokerPermission> missing = EnumSet.noneOf(BrokerPermission.class);
        missing.addAll(required);
        missing.removeAll(result.granted());

        return switch (result.outcome()) {
            case SUCCESS -> missing.isEmpty()
                    ? response(CredentialValidationOutcome.VALID, result.granted(), Set.of(), validatedAt,
                    "VALIDATED", "Broker credentials validated")
                    : response(CredentialValidationOutcome.INSUFFICIENT_PERMISSIONS, result.granted(), missing,
                    validatedAt, "MISSING_READ_PERMISSIONS", "Required read-only permissions are missing");
            case INVALID_CREDENTIALS -> response(CredentialValidationOutcome.INVALID_CREDENTIALS, Set.of(), required,
                    validatedAt, "AUTHENTICATION_REJECTED", "Broker rejected the credentials");
            case PERMISSION_DENIED -> response(CredentialValidationOutcome.INSUFFICIENT_PERMISSIONS,
                    result.granted(), missing, validatedAt, "PERMISSION_DENIED",
                    "Required read-only permissions are missing");
            case RATE_LIMITED -> response(CredentialValidationOutcome.RATE_LIMITED, result.granted(), missing,
                    validatedAt, "RATE_LIMITED", "Broker temporarily rate limited validation");
            case UNAVAILABLE -> response(CredentialValidationOutcome.BROKER_UNAVAILABLE, result.granted(), missing,
                    validatedAt, "BROKER_UNAVAILABLE", "Broker is temporarily unavailable");
            case UNEXPECTED_RESPONSE -> response(CredentialValidationOutcome.UNEXPECTED_PROVIDER_RESPONSE,
                    result.granted(), missing, validatedAt, "UNEXPECTED_RESPONSE",
                    "Broker response could not be validated safely");
        };
    }

    private CredentialValidationResult response(CredentialValidationOutcome outcome,
                                                Set<BrokerPermission> granted,
                                                Set<BrokerPermission> missing,
                                                Instant at, String code, String message) {
        return new CredentialValidationResult(outcome, null, granted, missing, at,
                new SafeProviderDiagnostics(code, message));
    }
}
