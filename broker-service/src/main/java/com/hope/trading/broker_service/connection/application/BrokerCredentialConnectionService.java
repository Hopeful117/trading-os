package com.hope.trading.broker_service.connection.application;

import com.hope.trading.broker_service.connection.api.BrokerConnectionResponse;
import com.hope.trading.broker_service.connection.api.CredentialValidationResponse;
import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.connection.integration.TradingCoreBrokerAccountClient;
import com.hope.trading.broker_service.credential.application.BrokerCredentialSource;
import com.hope.trading.broker_service.credential.application.BrokerCredentialValidator;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.credential.domain.CredentialValidationOutcome;
import com.hope.trading.broker_service.credential.domain.CredentialValidationResult;
import com.hope.trading.broker_service.secret.application.SecretRevoker;
import com.hope.trading.broker_service.secret.application.SecretRotator;
import com.hope.trading.broker_service.secret.application.SecretWriter;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.NewSecret;
import com.hope.trading.broker_service.secret.domain.SecretMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "trading-os.broker.credentials.source", havingValue = "stored")
public class BrokerCredentialConnectionService {
    private final BrokerConnectionRepository connectionRepository;
    private final TradingCoreBrokerAccountClient tradingCore;
    private final Map<BrokerProviderId, BrokerCredentialValidator> validators;
    private final SecretWriter secretWriter;
    private final SecretRotator secretRotator;
    private final SecretRevoker secretRevoker;
    private final BrokerCredentialSource credentialSource;
    private final Clock clock;

    public BrokerCredentialConnectionService(BrokerConnectionRepository connectionRepository,
                                             TradingCoreBrokerAccountClient tradingCore,
                                             List<BrokerCredentialValidator> validators,
                                             SecretWriter secretWriter,
                                             SecretRotator secretRotator,
                                             SecretRevoker secretRevoker,
                                             BrokerCredentialSource credentialSource,
                                             Clock clock) {
        this.connectionRepository = connectionRepository;
        this.tradingCore = tradingCore;
        this.validators = validators.stream().collect(Collectors.toUnmodifiableMap(
                BrokerCredentialValidator::provider, Function.identity()));
        this.secretWriter = secretWriter;
        this.secretRotator = secretRotator;
        this.secretRevoker = secretRevoker;
        this.credentialSource = credentialSource;
        this.clock = clock;
    }

    @Transactional
    public CredentialValidationResponse connect(UUID ownerId, UUID accountId, CredentialMaterial credentials,
                                                String authorization) {
        TradingCoreBrokerAccountClient.BrokerAccountContract account = tradingCore.findOwned(accountId, authorization);
        BrokerConnection connection = connectionRepository.findByBrokerAccountId(accountId)
                .orElseGet(() -> BrokerConnection.create(accountId, ownerId, account.provider(), clock.instant()));
        requireOwner(connection, ownerId);
        connection.pending(clock.instant());
        connectionRepository.save(connection);
        callback(connection, BrokerConnectionStatus.PENDING_VALIDATION, null, null, clock.instant(), authorization);

        CredentialValidationResult validation = validator(account.provider()).validate(credentials);
        if (validation.outcome() != CredentialValidationOutcome.VALID) {
            applyFailure(connection, validation);
            connectionRepository.save(connection);
            callback(connection, connection.technicalStatus(), null, null, validation.validatedAt(), authorization);
            return response(connection, validation);
        }

        String hint = credentials.apiKeyHint();
        try (NewSecret secret = serialize(credentials)) {
            CredentialReference reference = secretWriter.write(secret,
                    new SecretMetadata(accountId, account.provider(), hint));
            connection.connected(reference.value(), validation.detectedPermissions(),
                    validation.externalAccountId(), hint, validation.validatedAt());
            connectionRepository.save(connection);
            callback(connection, BrokerConnectionStatus.CONNECTED, reference.value(),
                    validation.externalAccountId(), validation.validatedAt(), authorization);
        }
        return response(connection, validation);
    }

    @Transactional
    public CredentialValidationResponse rotate(UUID ownerId, UUID accountId, CredentialMaterial replacement,
                                               String authorization) {
        tradingCore.findOwned(accountId, authorization);
        BrokerConnection connection = requireConnection(ownerId, accountId);
        CredentialValidationResult validation = validator(connection.provider()).validate(replacement);
        if (validation.outcome() != CredentialValidationOutcome.VALID) {
            // ADR-024: a failed rotation must not alter the active version or connected state.
            return response(connection, validation);
        }
        CredentialReference current = new CredentialReference(connection.activeCredentialReference());
        String hint = replacement.apiKeyHint();
        try (NewSecret secret = serialize(replacement)) {
            CredentialReference next = secretRotator.rotate(current, secret,
                    new SecretMetadata(accountId, connection.provider(), hint));
            connection.connected(next.value(), validation.detectedPermissions(),
                    validation.externalAccountId(), hint, validation.validatedAt());
            connectionRepository.save(connection);
            callback(connection, BrokerConnectionStatus.CONNECTED, next.value(),
                    validation.externalAccountId(), validation.validatedAt(), authorization);
        }
        return response(connection, validation);
    }

    @Transactional
    public CredentialValidationResponse validate(UUID ownerId, UUID accountId, String authorization) {
        tradingCore.findOwned(accountId, authorization);
        BrokerConnection connection = requireConnection(ownerId, accountId);
        try (CredentialMaterial material = credentialSource.resolve(
                new CredentialReference(connection.activeCredentialReference()))) {
            CredentialValidationResult validation = validator(connection.provider()).validate(material);
            if (validation.outcome() == CredentialValidationOutcome.VALID) {
                connection.connected(connection.activeCredentialReference(), validation.detectedPermissions(),
                        validation.externalAccountId(), connection.apiKeyHint(), validation.validatedAt());
                callback(connection, BrokerConnectionStatus.CONNECTED, connection.activeCredentialReference(),
                        validation.externalAccountId(), validation.validatedAt(), authorization);
            } else {
                applyFailure(connection, validation);
                callback(connection, connection.technicalStatus(), connection.activeCredentialReference(),
                        connection.externalAccountId(), validation.validatedAt(), authorization);
            }
            return response(connection, validation);
        }
    }

    @Transactional(readOnly = true)
    public BrokerConnectionResponse get(UUID ownerId, UUID accountId, String authorization) {
        tradingCore.findOwned(accountId, authorization);
        return map(requireConnection(ownerId, accountId));
    }

    @Transactional
    public void revoke(UUID ownerId, UUID accountId, String authorization) {
        tradingCore.findOwned(accountId, authorization);
        BrokerConnection connection = requireConnection(ownerId, accountId);
        if (connection.activeCredentialReference() != null) {
            secretRevoker.revoke(new CredentialReference(connection.activeCredentialReference()), "USER_REQUEST");
        }
        connection.revoke(clock.instant());
        callback(connection, BrokerConnectionStatus.REVOKED, null, connection.externalAccountId(),
                clock.instant(), authorization);
    }

    @Transactional
    public void disconnect(UUID ownerId, UUID accountId, String authorization) {
        tradingCore.findOwned(accountId, authorization);
        BrokerConnection connection = requireConnection(ownerId, accountId);
        connection.disconnect(clock.instant());
        callback(connection, BrokerConnectionStatus.DISCONNECTED, connection.activeCredentialReference(),
                connection.externalAccountId(), clock.instant(), authorization);
    }

    private BrokerConnection requireConnection(UUID ownerId, UUID accountId) {
        return connectionRepository.findByBrokerAccountIdAndOwnerId(accountId, ownerId)
                .orElseThrow(BrokerConnectionNotFoundException::new);
    }

    private void requireOwner(BrokerConnection connection, UUID ownerId) {
        if (!connection.ownerId().equals(ownerId)) throw new BrokerAccountOwnershipException();
    }

    private BrokerCredentialValidator validator(BrokerProviderId provider) {
        BrokerCredentialValidator validator = validators.get(provider);
        if (validator == null) throw new IllegalArgumentException("Broker provider is not supported");
        return validator;
    }

    private void applyFailure(BrokerConnection connection, CredentialValidationResult validation) {
        BrokerConnectionStatus status = switch (validation.outcome()) {
            case INVALID_CREDENTIALS, UNSUPPORTED_CREDENTIAL_FORMAT -> BrokerConnectionStatus.INVALID_CREDENTIALS;
            case INSUFFICIENT_PERMISSIONS -> BrokerConnectionStatus.INSUFFICIENT_PERMISSIONS;
            case BROKER_UNAVAILABLE, RATE_LIMITED, UNEXPECTED_PROVIDER_RESPONSE ->
                    BrokerConnectionStatus.TEMPORARILY_UNAVAILABLE;
            case VALID -> throw new IllegalArgumentException("VALID is not a failure");
        };
        connection.validationFailed(status, validation.validatedAt());
    }

    private NewSecret serialize(CredentialMaterial material) {
        char[] key = material.copyApiKey();
        char[] secret = material.copyApiSecret();
        char[] passphrase = material.copyPassphrase();
        byte[] keyBytes = new String(key).getBytes(StandardCharsets.UTF_8);
        byte[] secretBytes = new String(secret).getBytes(StandardCharsets.UTF_8);
        byte[] passphraseBytes = new String(passphrase).getBytes(StandardCharsets.UTF_8);
        try {
            ByteBuffer buffer = ByteBuffer.allocate(12 + keyBytes.length + secretBytes.length + passphraseBytes.length);
            buffer.putInt(keyBytes.length).put(keyBytes);
            buffer.putInt(secretBytes.length).put(secretBytes);
            buffer.putInt(passphraseBytes.length).put(passphraseBytes);
            return new NewSecret(buffer.array());
        } finally {
            Arrays.fill(key, '\0');
            Arrays.fill(secret, '\0');
            Arrays.fill(passphrase, '\0');
            Arrays.fill(keyBytes, (byte) 0);
            Arrays.fill(secretBytes, (byte) 0);
            Arrays.fill(passphraseBytes, (byte) 0);
        }
    }

    private void callback(BrokerConnection connection, BrokerConnectionStatus status, UUID reference,
                          String externalId, java.time.Instant at, String authorization) {
        tradingCore.updateStatus(connection.brokerAccountId(),
                new TradingCoreBrokerAccountClient.ConnectionStatusUpdate(status, reference, externalId, at),
                authorization);
    }

    private CredentialValidationResponse response(BrokerConnection connection,
                                                  CredentialValidationResult validation) {
        return new CredentialValidationResponse(validation.outcome(), connection.technicalStatus(),
                validation.missingPermissions(), validation.validatedAt(),
                validation.diagnostics() == null ? "Validation completed" : validation.diagnostics().safeMessage());
    }

    private BrokerConnectionResponse map(BrokerConnection connection) {
        return new BrokerConnectionResponse(connection.brokerAccountId(), connection.provider(),
                connection.technicalStatus(), connection.externalAccountId(), connection.apiKeyHint(),
                connection.detectedPermissions(), connection.lastValidatedAt());
    }
}
