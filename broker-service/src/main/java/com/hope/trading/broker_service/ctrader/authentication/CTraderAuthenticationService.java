package com.hope.trading.broker_service.ctrader.authentication;

import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.connection.domain.BrokerProviderRegistrationCatalog;
import com.hope.trading.broker_service.connection.integration.TradingCoreBrokerAccountClient;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import com.hope.trading.broker_service.ctrader.synchronization.CTraderReadOnlyObservation;
import com.hope.trading.broker_service.secret.application.SecretReader;
import com.hope.trading.broker_service.secret.application.SecretRotator;
import com.hope.trading.broker_service.secret.application.SecretWriter;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.NewSecret;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import com.hope.trading.broker_service.secret.domain.SecretMetadata;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.List;
import java.util.UUID;

@Service
public class CTraderAuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(CTraderAuthenticationService.class);
    private static final String SCOPE = "trading";
    private static final Set<BrokerPermission> READ_PERMISSIONS = Set.of(
            BrokerPermission.READ_ACCOUNT,
            BrokerPermission.READ_BALANCES,
            BrokerPermission.READ_POSITIONS,
            BrokerPermission.READ_ORDERS,
            BrokerPermission.READ_TRADE_HISTORY);
    private final CTraderAuthorizationAttemptRepository attempts;
    private final CTraderAuthorizationAttemptConsumer attemptConsumer;
    private final BrokerConnectionRepository connections;
    private final TradingCoreBrokerAccountClient tradingCore;
    private final BrokerProviderRegistrationCatalog providers;
    private final CTraderProperties properties;
    private final CTraderTokenClient tokenClient;
    private final CTraderOpenApiClient openApi;
    private final SecretWriter secretWriter;
    private final SecretReader secretReader;
    private final SecretRotator secretRotator;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public CTraderAuthenticationService(CTraderAuthorizationAttemptRepository attempts,
                                        CTraderAuthorizationAttemptConsumer attemptConsumer,
                                        BrokerConnectionRepository connections,
                                        TradingCoreBrokerAccountClient tradingCore,
                                        BrokerProviderRegistrationCatalog providers,
                                        CTraderProperties properties,
                                        CTraderTokenClient tokenClient,
                                        CTraderOpenApiClient openApi,
                                        SecretWriter secretWriter,
                                        SecretReader secretReader,
                                        SecretRotator secretRotator,
                                        Clock clock) {
        this.attempts = attempts;
        this.attemptConsumer = attemptConsumer;
        this.connections = connections;
        this.tradingCore = tradingCore;
        this.providers = providers;
        this.properties = properties;
        this.tokenClient = tokenClient;
        this.openApi = openApi;
        this.secretWriter = secretWriter;
        this.secretReader = secretReader;
        this.secretRotator = secretRotator;
        this.clock = clock;
    }

    @Transactional
    public CTraderAuthorizationResponse initiate(UUID ownerId, UUID brokerAccountId, String authorization) {
        if (properties.clientId() == null || properties.clientId().isBlank()
                || properties.redirectUri() == null || properties.redirectUri().isBlank()) {
            throw new CTraderAuthenticationException("cTrader authorization is not configured");
        }
        TradingCoreBrokerAccountClient.BrokerAccountContract account = tradingCore.findOwned(brokerAccountId, authorization);
        UUID ctraderReference = providers.referenceFor(BrokerProviderId.CTRADER);
        if (!ctraderReference.equals(account.providerRegistrationReference())) {
            throw new CTraderAuthenticationException("Broker account is not a cTrader account");
        }
        BrokerConnection connection = connections.findByBrokerAccountIdForUpdate(brokerAccountId)
                .orElseGet(() -> BrokerConnection.create(brokerAccountId, ownerId, BrokerProviderId.CTRADER, clock.instant()));
        if (!connection.ownerId().equals(ownerId) || connection.provider() != BrokerProviderId.CTRADER) {
            throw new CTraderAuthenticationException("Broker connection is not accessible");
        }
        connections.save(connection);
        String state = randomState();
        Instant now = clock.instant();
        CTraderAuthorizationAttempt attempt = CTraderAuthorizationAttempt.initiate(ownerId, brokerAccountId,
                ctraderReference, hash(state), properties.environment(), now,
                now.plusSeconds(properties.authorizationStateTtlSeconds()));
        attempt.permissionScope(SCOPE);
        attempts.save(attempt);
        String url = properties.authorizationEndpoint()
                + "?client_id=" + encode(properties.clientId())
                + "&redirect_uri=" + encode(properties.redirectUri())
                + "&scope=" + SCOPE
                + "&product=web"
                + "&state=" + encode(state);
        return response(attempt, url, "Authorization initiated");
    }

    @Transactional(noRollbackFor = CTraderAuthenticationException.class)
    public CTraderAuthorizationResponse callback(String state, String code, String error) {
        if (state == null || state.isBlank()) throw new CTraderAuthenticationException("Authorization state is required");
        Instant now = clock.instant();
        UUID attemptId = attemptConsumer.consume(hash(state), now);
        CTraderAuthorizationAttempt attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new CTraderAuthenticationException("Authorization state is invalid"));
        if (attempt.status() != CTraderAuthorizationAttemptStatus.INITIATED) {
            return response(attempt, null, safeStatus(attempt));
        }
        try {
            if (error != null && !error.isBlank()) throw new CTraderAuthenticationException("Authorization was denied");
            if (code == null || code.isBlank()) throw new CTraderAuthenticationException("Authorization code is missing");
            CTraderToken token = tokenClient.exchangeAuthorizationCode(code, properties.redirectUri());
            List<String> accountIds = openApi.discoverAuthorizedAccountIds(token);
            if (accountIds.isEmpty()) throw new CTraderAuthenticationException("No authorized cTrader account is available");
            UUID tokenReference = persistToken(attempt, token);
            attempt.discovered(tokenReference, accountIds);
            attempts.save(attempt);
            return response(attempt, null, "Select an authorized cTrader account");
        } catch (CTraderAuthenticationException exception) {
            if (attempt.status() == CTraderAuthorizationAttemptStatus.INITIATED) {
                attemptConsumer.fail(attempt.id(), safeCode(exception));
            }
            throw exception;
        } catch (RuntimeException exception) {
            attemptConsumer.fail(attempt.id(), "AUTHENTICATION_FAILED");
            throw exception;
        }
    }

    @Transactional
    public CTraderAuthorizationResponse get(UUID ownerId, UUID brokerAccountId, UUID attemptId) {
        CTraderAuthorizationAttempt attempt = attempts.findByIdAndOwnerId(attemptId, ownerId)
                .orElseThrow(() -> new CTraderAuthenticationException("Authorization attempt is not accessible"));
        if (!attempt.brokerAccountId().equals(brokerAccountId)) {
            throw new CTraderAuthenticationException("Authorization attempt is not accessible");
        }
        if (attempt.status() == CTraderAuthorizationAttemptStatus.INITIATED
                && !clock.instant().isBefore(attempt.expiresAt())) {
            attempt.expire();
            attempts.save(attempt);
        }
        return response(attempt, null, safeStatus(attempt));
    }

    @Transactional
    public CTraderAuthorizationResponse bind(UUID ownerId, UUID brokerAccountId, UUID attemptId,
                                             String externalAccountId, String authorization) {
        CTraderAuthorizationAttempt attempt = attempts.findByIdAndOwnerId(attemptId, ownerId)
                .orElseThrow(() -> new CTraderAuthenticationException("Authorization attempt is not accessible"));
        if (!attempt.brokerAccountId().equals(brokerAccountId)) {
            throw new CTraderAuthenticationException("Authorization attempt is not accessible");
        }
        if (attempt.status() != CTraderAuthorizationAttemptStatus.ACCOUNT_SELECTION_REQUIRED
                || !clock.instant().isBefore(attempt.expiresAt())) {
            throw new CTraderAuthenticationException("Authorization attempt is not selectable");
        }
        if (attempt.tokenCredentialReference() == null) throw new CTraderAuthenticationException("Authorization is not complete");
        TradingCoreBrokerAccountClient.BrokerAccountContract account = tradingCore.findOwned(attempt.brokerAccountId(), authorization);
        UUID ctraderReference = providers.referenceFor(BrokerProviderId.CTRADER);
        if (!ctraderReference.equals(account.providerRegistrationReference())) {
            throw new CTraderAuthenticationException("Broker account is not a cTrader account");
        }
        try (PlainSecret secret = secretReader.read(new CredentialReference(attempt.tokenCredentialReference()))) {
            CTraderToken token = CTraderTokenCodec.decode(secret.copyValue());
            if (token.expiresAt().isBefore(clock.instant())) throw new CTraderAuthenticationException("cTrader authentication has expired");
            attempt.bind(externalAccountId);
            openApi.authorizeAccount(token, externalAccountId);
            if (!openApi.isDemoAccount(token, externalAccountId)) {
                throw new CTraderAuthenticationException("Selected cTrader account is not a provider-confirmed demo account");
            }
            Instant validatedAt = clock.instant();
            CTraderReadOnlyObservation observation = openApi.readOnlyObservation(
                    token, externalAccountId, validatedAt.minusSeconds(7 * 24 * 60 * 60), validatedAt);
            if (!externalAccountId.equals(observation.externalAccountId())) {
                throw new CTraderAuthenticationException("cTrader external account identity mismatch");
            }
            if (!observation.complete()) {
                log.warn("ctrader_account_observation_incomplete accountId={} reasons={}",
                        externalAccountId, observation.reasons());
                throw new CTraderAuthenticationException("cTrader account observation is incomplete");
            }
            BrokerConnection connection = connections.findByBrokerAccountIdAndOwnerIdForUpdate(
                            attempt.brokerAccountId(), ownerId)
                    .orElseThrow(() -> new CTraderAuthenticationException("Broker connection is not accessible"));
            if (connection.provider() != BrokerProviderId.CTRADER
                    || !connection.ownerId().equals(ownerId)) {
                throw new CTraderAuthenticationException("Broker connection is not accessible");
            }
            connection.bindExternalAccount(externalAccountId, attempt.environment(), validatedAt);
            connection.confirmProviderAccountEnvironment(false, validatedAt);
            Set<BrokerPermission> permissions = new java.util.HashSet<>(READ_PERMISSIONS);
            if ("trading".equalsIgnoreCase(attempt.permissionScope())) permissions.add(BrokerPermission.TRADE);
            connection.connected(new CredentialReference(attempt.tokenCredentialReference()).value(),
                    permissions, externalAccountId, null, validatedAt);
            connections.save(connection);
            attempts.save(attempt);
            tradingCore.updateStatusWithServiceAuthorization(attempt.brokerAccountId(),
                    new TradingCoreBrokerAccountClient.ConnectionStatusUpdate(
                            BrokerConnectionStatus.CONNECTED,
                            attempt.tokenCredentialReference(),
                            externalAccountId,
                            validatedAt),
                    null);
            return response(attempt, null, "cTrader account bound and read-only connection validated");
        }
    }

    private UUID persistToken(CTraderAuthorizationAttempt attempt, CTraderToken token) {
        try (NewSecret secret = new NewSecret(CTraderTokenCodec.encode(token))) {
            BrokerConnection connection = connections.findByBrokerAccountIdAndOwnerIdForUpdate(
                            attempt.brokerAccountId(), attempt.ownerId())
                    .orElseThrow(() -> new CTraderAuthenticationException("Broker connection is not accessible"));
            if (connection.provider() != BrokerProviderId.CTRADER
                    || !connection.ownerId().equals(attempt.ownerId())) {
                throw new CTraderAuthenticationException("Broker connection is not accessible");
            }
            CredentialReference current = connection.activeCredentialReference() == null
                    ? null : new CredentialReference(connection.activeCredentialReference());
            CredentialReference reference = current == null
                    ? secretWriter.write(secret, new SecretMetadata(attempt.brokerAccountId(), BrokerProviderId.CTRADER, "ctrader-token"))
                    : secretRotator.rotate(current, secret, new SecretMetadata(attempt.brokerAccountId(), BrokerProviderId.CTRADER, "ctrader-token"));
            connection.authenticate(reference.value(), clock.instant());
            connections.save(connection);
            return reference.value();
        }
    }

    private CTraderAuthorizationResponse response(CTraderAuthorizationAttempt attempt, String url, String message) {
        return new CTraderAuthorizationResponse(attempt.id(), url, attempt.status(),
                attempt.discoveredAccountIds(), attempt.selectedExternalAccountId(), attempt.expiresAt(), message);
    }

    private String safeStatus(CTraderAuthorizationAttempt attempt) {
        return switch (attempt.status()) {
            case INITIATED -> "Authorization pending";
            case ACCOUNT_SELECTION_REQUIRED -> "Select an authorized cTrader account";
            case BOUND -> "cTrader account bound";
            case FAILED -> "Authorization failed";
            case EXPIRED -> "Authorization expired";
            case REVOKED -> "Authorization revoked";
        };
    }

    private String safeCode(CTraderAuthenticationException exception) {
        return exception.getMessage().toLowerCase().contains("state") ? "INVALID_STATE" : "AUTHENTICATION_FAILED";
    }

    private String randomState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
