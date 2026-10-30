package com.hope.trading.broker_service.ctrader.position;

import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.PositionManagementCapability;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.ctrader.authentication.CTraderAuthenticationException;
import com.hope.trading.broker_service.ctrader.authentication.CTraderOpenApiClient;
import com.hope.trading.broker_service.ctrader.authentication.CTraderToken;
import com.hope.trading.broker_service.ctrader.authentication.CTraderTokenCodec;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import com.hope.trading.broker_service.ctrader.authentication.CTraderRequestSafetyGuard;
import com.hope.trading.broker_service.ctrader.synchronization.CTraderReadOnlyObservation;
import com.hope.trading.broker_service.secret.application.SecretReader;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Component
public final class CTraderPositionManagementCapability implements PositionManagementCapability {
    private static final String SCOPE_PREFIX = "CTRADER|";
    private static final Duration MAX_OBSERVATION_AGE = Duration.ofSeconds(30);

    private final BrokerConnectionRepository connections;
    private final SecretReader secrets;
    private final CTraderOpenApiClient client;
    private final CTraderProperties properties;
    private final CTraderRequestSafetyGuard safety;
    private final Clock clock;

    @Autowired
    public CTraderPositionManagementCapability(BrokerConnectionRepository connections,
                                               SecretReader secrets,
                                               CTraderOpenApiClient client,
                                               CTraderProperties properties,
                                               CTraderRequestSafetyGuard safety,
                                               Clock clock) {
        this.connections = connections;
        this.secrets = secrets;
        this.client = client;
        this.properties = properties;
        this.safety = safety;
        this.clock = clock;
    }

    @Override
    public ResolvedPositionCloseTarget resolveTarget(ResolveTargetRequest request) {
        BrokerConnection connection = requireConnection(request.brokerAccountId(), false);
        try (PlainSecret secret = secrets.read(new CredentialReference(connection.activeCredentialReference()))) {
            CTraderToken token = validToken(secret);
            CTraderReadOnlyObservation observation = readObservation(token, connection);
            CTraderReadOnlyObservation.Position position = exactPosition(observation,
                    request.brokerPositionReference());
            String scope = String.join("|", SCOPE_PREFIX.substring(0, SCOPE_PREFIX.length() - 1),
                    connection.externalAccountId(), position.providerReference(), position.instrument(),
                    absolute(position.signedQuantity()).toPlainString(), observation.completedAt().toString());
            return new ResolvedPositionCloseTarget(request.brokerAccountId(), scope);
        }
    }

    @Override
    public CloseResult executeClose(ExecuteCloseRequest request) {
        try {
            safety.requireExecutionEnabled();
            BrokerConnection connection = requireConnection(request.brokerAccountId(), true);
            Scope scope = Scope.parse(request.resolvedMutationScope());
            if (!scope.externalAccountId().equals(connection.externalAccountId())) {
                return new CloseRejected(null, "CTRADER_ACCOUNT_MISMATCH");
            }
            try (PlainSecret secret = secrets.read(new CredentialReference(connection.activeCredentialReference()))) {
                CTraderToken token = validToken(secret);
                CTraderReadOnlyObservation observation = readObservation(token, connection);
                CTraderReadOnlyObservation.Position position = exactPosition(observation, scope.positionId());
                BigDecimal currentQuantity = absolute(position.signedQuantity());
                BigDecimal quantity = request.closeQuantity() == null ? currentQuantity : request.closeQuantity();
                if (request.closeQuantity() != null && quantity.compareTo(currentQuantity) >= 0) {
                    return new CloseRejected(null, "CTRADER_PARTIAL_QUANTITY_MUST_BE_LESS_THAN_POSITION");
                }
                if (quantity.compareTo(currentQuantity) > 0) {
                    return new CloseRejected(null, "CTRADER_CLOSE_QUANTITY_ABOVE_POSITION");
                }
                CTraderOpenApiClient.CTraderClosePositionResponse response = client.closePosition(token,
                        new CTraderOpenApiClient.CTraderClosePositionRequest(
                                connection.externalAccountId(), Long.parseLong(scope.positionId()),
                                position.instrument(), quantity, clientRequestId(request.idempotencyKey())));
                return new CloseAcknowledged(response.externalOrderId(), response.correlationId());
            }
        } catch (CTraderAuthenticationException exception) {
            return uncertain(exception)
                    ? new CloseUnknown("CTRADER_CLOSE_OUTCOME_UNKNOWN")
                    : new CloseRejected(null, safeCode(exception));
        } catch (RuntimeException exception) {
            return new CloseUnknown("CTRADER_CLOSE_OUTCOME_UNKNOWN");
        }
    }

    @Override
    public ReconciliationCloseResult reconcile(ReconcileCloseRequest request) {
        try {
            BrokerConnection connection = requireConnection(request.brokerAccountId(), false);
            Scope scope = Scope.parse(request.resolvedMutationScope());
            if (!scope.externalAccountId().equals(connection.externalAccountId())) {
                return new Inconclusive("CTRADER_ACCOUNT_MISMATCH");
            }
            try (PlainSecret secret = secrets.read(new CredentialReference(connection.activeCredentialReference()))) {
                CTraderToken token = validToken(secret);
                CTraderReadOnlyObservation observation = readObservation(token, connection);
                if (!observation.complete() || observation.completedAt().isBefore(clock.instant().minus(MAX_OBSERVATION_AGE))) {
                    return new Inconclusive("CTRADER_OBSERVATION_NOT_AUTHORITATIVE");
                }
                CTraderReadOnlyObservation.Position position = findPosition(observation, scope.positionId());
                if (position != null) {
                    BigDecimal remaining = absolute(position.signedQuantity());
                    BigDecimal closedQuantity = scope.originalQuantity().subtract(remaining);
                    if (remaining.compareTo(scope.originalQuantity()) < 0) {
                        return hasCorrelatedClosingDeal(observation, scope, closedQuantity)
                                ? new ExposureConfirmedPartial(remaining)
                                : new Inconclusive("CTRADER_PARTIAL_CLOSE_NOT_CONFIRMED");
                    }
                    return new CommandConfirmedNotExecuted();
                }
                boolean closingDeal = hasCorrelatedClosingDeal(observation, scope, scope.originalQuantity());
                return closingDeal ? new ExposureConfirmedAbsent()
                        : new Inconclusive("CTRADER_CLOSE_DEAL_NOT_CONFIRMED");
            }
        } catch (RuntimeException exception) {
            return new Inconclusive("CTRADER_RECONCILIATION_UNAVAILABLE");
        }
    }

    private BrokerConnection requireConnection(UUID brokerAccountId, boolean mutation) {
        BrokerConnection connection = connections.findByBrokerAccountId(brokerAccountId)
                .orElseThrow(() -> new CTraderAuthenticationException("cTrader connection is unavailable"));
        if (connection.provider() != BrokerProviderId.CTRADER
                || connection.externalAccountId() == null
                || connection.activeCredentialReference() == null
                || connection.providerEnvironment() == null
                || !connection.providerEnvironment().equalsIgnoreCase(properties.environment())
                || connection.technicalStatus() == BrokerConnectionStatus.DISCONNECTED
                || connection.technicalStatus() == BrokerConnectionStatus.REVOKED
                || connection.technicalStatus() == BrokerConnectionStatus.REAUTHENTICATION_REQUIRED
                || connection.technicalStatus() == BrokerConnectionStatus.INVALID_CREDENTIALS
                || connection.technicalStatus() == BrokerConnectionStatus.INSUFFICIENT_PERMISSIONS
                || connection.technicalStatus() == BrokerConnectionStatus.TEMPORARILY_UNAVAILABLE
                || !effectiveDemoEnvironment(connection)
                || (mutation && !connection.detectedPermissions().contains(BrokerPermission.TRADE))) {
            throw new CTraderAuthenticationException(mutation
                    ? "cTrader demo position management is not admitted"
                    : "cTrader connection is not usable");
        }
        return connection;
    }

    private CTraderToken validToken(PlainSecret secret) {
        CTraderToken token = CTraderTokenCodec.decode(secret.copyValue());
        if (!token.expiresAt().isAfter(clock.instant())) {
            throw new CTraderAuthenticationException("cTrader authentication has expired");
        }
        return token;
    }

    private CTraderReadOnlyObservation readObservation(CTraderToken token, BrokerConnection connection) {
        Instant now = clock.instant();
        CTraderReadOnlyObservation observation = client.readOnlyObservation(token,
                connection.externalAccountId(), now.minus(Duration.ofDays(7)), now);
        if (!connection.externalAccountId().equals(observation.externalAccountId())) {
            throw new CTraderAuthenticationException("cTrader external account identity mismatch");
        }
        if (!observation.complete() || observation.completedAt().isBefore(now.minus(MAX_OBSERVATION_AGE))) {
            throw new CTraderAuthenticationException("cTrader authoritative snapshot is not fresh and complete");
        }
        return observation;
    }

    private CTraderReadOnlyObservation.Position exactPosition(CTraderReadOnlyObservation observation, String reference) {
        CTraderReadOnlyObservation.Position position = findPosition(observation, reference);
        if (position == null) throw new CTraderAuthenticationException("CTRADER_POSITION_NOT_FOUND");
        return position;
    }

    private CTraderReadOnlyObservation.Position findPosition(CTraderReadOnlyObservation observation, String reference) {
        return observation.positions().stream()
                .filter(position -> reference.equals(position.providerReference()))
                .reduce((first, second) -> {
                    throw new CTraderAuthenticationException("CTRADER_AMBIGUOUS_POSITION");
                }).orElse(null);
    }

    private boolean hasCorrelatedClosingDeal(CTraderReadOnlyObservation observation, Scope scope,
                                             BigDecimal minimumQuantity) {
        return observation.deals().stream()
                .anyMatch(deal -> scope.positionId().equals(deal.providerPositionReference())
                        && deal.closingPosition()
                        && deal.executedAt().isAfter(scope.observationCompletedAt())
                        && absolute(deal.quantity()).compareTo(minimumQuantity) >= 0);
    }

    private boolean effectiveDemoEnvironment(BrokerConnection connection) {
        if (Boolean.FALSE.equals(connection.providerAccountLive())) return true;
        return Boolean.TRUE.equals(connection.providerAccountLive())
                && "FTMO_FREE".equalsIgnoreCase(properties.accountProgram())
                && properties.environment() != null
                && properties.environment().equalsIgnoreCase(connection.providerEnvironment());
    }

    private static BigDecimal absolute(BigDecimal value) {
        return value.abs();
    }

    private String clientRequestId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private boolean uncertain(CTraderAuthenticationException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("timeout") || message.contains("transport")
                || message.contains("submission failed") || message.contains("response is invalid")
                || message.contains("unavailable");
    }

    private String safeCode(CTraderAuthenticationException exception) {
        String message = exception.getMessage() == null ? "CTRADER_CLOSE_FAILED" : exception.getMessage();
        String normalized = message.replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(Locale.ROOT)
                .replaceAll("_+$", "");
        return normalized.startsWith("CTRADER_") ? normalized.substring(0, Math.min(56, normalized.length()))
                : "CTRADER_" + normalized.substring(0, Math.min(48, normalized.length()));
    }

    private record Scope(String externalAccountId, String positionId, String instrument,
                         BigDecimal originalQuantity, Instant observationCompletedAt) {
        static Scope parse(String value) {
            String[] parts = value.split("\\|", -1);
            if (parts.length != 6 || !SCOPE_PREFIX.substring(0, SCOPE_PREFIX.length() - 1).equals(parts[0])
                    || parts[1].isBlank() || parts[2].isBlank() || parts[3].isBlank()) {
                throw new CTraderAuthenticationException("CTRADER_INVALID_POSITION_SCOPE");
            }
            try {
                Long.parseLong(parts[2]);
                BigDecimal original = new BigDecimal(parts[4]);
                if (original.signum() <= 0) throw new NumberFormatException();
                return new Scope(parts[1], parts[2], parts[3], original, Instant.parse(parts[5]));
            } catch (NumberFormatException exception) {
                throw new CTraderAuthenticationException("CTRADER_INVALID_POSITION_SCOPE", exception);
            }
        }
    }
}
