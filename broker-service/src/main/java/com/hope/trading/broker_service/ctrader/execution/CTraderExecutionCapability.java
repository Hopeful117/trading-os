package com.hope.trading.broker_service.ctrader.execution;

import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.ExecutionCapability;
import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.ReconciliationCapability;
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
import com.hope.trading.broker_service.ctrader.authentication.CTraderSafetyException;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import com.hope.trading.broker_service.ctrader.config.CTraderSafetyProperties;
import com.hope.trading.broker_service.secret.application.SecretReader;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Component
public final class CTraderExecutionCapability implements ExecutionCapability, ReconciliationCapability {
    private final BrokerConnectionRepository connections;
    private final SecretReader secrets;
    private final CTraderOpenApiClient client;
    private final CTraderProperties properties;
    private final CTraderSafetyProperties safety;
    private final Clock clock;

    public CTraderExecutionCapability(BrokerConnectionRepository connections, SecretReader secrets,
                                      CTraderOpenApiClient client, CTraderProperties properties, Clock clock) {
        this(connections, secrets, client, properties, clock,
                new CTraderSafetyProperties(false, false, 1000, 700, 800,
                        Duration.ofMinutes(20), Duration.ofMinutes(30)));
    }

    @Autowired
    public CTraderExecutionCapability(BrokerConnectionRepository connections, SecretReader secrets,
                                      CTraderOpenApiClient client, CTraderProperties properties, Clock clock,
                                      CTraderSafetyProperties safety) {
        this.connections = connections;
        this.secrets = secrets;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
        this.safety = safety;
    }

    public boolean enabled() {
        return safety.executionEnabled();
    }

    @Override
    public ExecutionResult execute(ExecutionRequest request) {
        try {
            if (!enabled()) throw new CTraderSafetyException("cTrader execution is disabled by safety policy");
            BrokerConnection connection = requireExecutionConnection(request.brokerAccountId());
            try (PlainSecret secret = secrets.read(new CredentialReference(connection.activeCredentialReference()))) {
                CTraderToken token = CTraderTokenCodec.decode(secret.copyValue());
                requireToken(token);
                requireFreshCompleteObservation(token, connection);
                CTraderOpenApiClient.CTraderOrderResponse response = client.submitOrder(token,
                        new CTraderOpenApiClient.CTraderOrderRequest(
                                connection.externalAccountId(), request.instrument(), request.side().name(),
                                request.orderType().name(), request.quantity(), request.limitPrice(),
                                request.stopLossPrice(), clientOrderId(request.idempotencyKey())));
                return new Acknowledged(response.externalOrderId(), request.executionAttemptId().toString());
            }
        } catch (CTraderAuthenticationException exception) {
            return isProviderUncertain(exception) ? new Unknown("CTRADER_OUTCOME_UNKNOWN")
                    : new Rejected(null, safeCode(exception));
        } catch (RuntimeException exception) {
            return new Unknown("CTRADER_EXECUTION_UNKNOWN");
        }
    }

    @Override
    public ReconciliationResult reconcile(ReconciliationRequest request) {
        try {
            BrokerConnection connection = requireExecutionConnection(request.brokerAccountId());
            try (PlainSecret secret = secrets.read(new CredentialReference(connection.activeCredentialReference()))) {
                CTraderToken token = CTraderTokenCodec.decode(secret.copyValue());
                requireToken(token);
                Instant now = clock.instant();
                var observation = client.readOnlyObservation(token, connection.externalAccountId(),
                        now.minus(Duration.ofDays(7)), now);
                String expectedClientOrderId = clientOrderId(request.idempotencyKey());
                var matches = observation.orders().stream()
                        .filter(order -> expectedClientOrderId.equals(order.clientReference()))
                        .toList();
                if (matches.isEmpty()) return new ConfirmedAbsent();
                if (matches.size() > 1) return new Inconsistent("MULTIPLE_MATCHING_ORDERS");
                var order = matches.getFirst();
                return new ReconciledOrder(order.providerReference(), request.executionAttemptId().toString(),
                        orderStatus(order.status()));
            }
        } catch (RuntimeException exception) {
            return new Inconsistent("CTRADER_RECONCILIATION_UNAVAILABLE");
        }
    }

    private BrokerConnection requireExecutionConnection(java.util.UUID brokerAccountId) {
        BrokerConnection connection = connections.findByBrokerAccountId(brokerAccountId)
                .orElseThrow(() -> new CTraderAuthenticationException("cTrader connection is unavailable"));
        if (connection.provider() != BrokerProviderId.CTRADER
                || connection.technicalStatus() != BrokerConnectionStatus.CONNECTED
                || connection.activeCredentialReference() == null
                || connection.externalAccountId() == null
                || !String.valueOf(properties.environment()).equalsIgnoreCase(connection.providerEnvironment())
                || !effectiveDemoEnvironment(connection)
                || !connection.detectedPermissions().contains(BrokerPermission.TRADE)) {
            throw new CTraderAuthenticationException("cTrader demo execution is not admitted");
        }
        return connection;
    }

    private boolean effectiveDemoEnvironment(BrokerConnection connection) {
        if (Boolean.FALSE.equals(connection.providerAccountLive())) return true;
        return Boolean.TRUE.equals(connection.providerAccountLive())
                && "FTMO_FREE".equalsIgnoreCase(properties.accountProgram())
                && properties.environment() != null
                && properties.environment().equalsIgnoreCase(connection.providerEnvironment());
    }

    private void requireToken(CTraderToken token) {
        if (!token.expiresAt().isAfter(clock.instant())) {
            throw new CTraderAuthenticationException("cTrader authentication has expired");
        }
    }

    private void requireFreshCompleteObservation(CTraderToken token, BrokerConnection connection) {
        Instant now = clock.instant();
        var observation = client.readOnlyObservation(token, connection.externalAccountId(),
                now.minus(Duration.ofDays(7)), now);
        if (!connection.externalAccountId().equals(observation.externalAccountId()) || !observation.complete()
                || observation.completedAt().isBefore(now.minusSeconds(30))) {
            throw new CTraderAuthenticationException("cTrader authoritative snapshot is not fresh and complete");
        }
    }

    private String clientOrderId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private OrderStatus orderStatus(String status) {
        return switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
            case "ORDER_STATUS_ACCEPTED", "ACCEPTED", "1" -> OrderStatus.ACKNOWLEDGED;
            case "ORDER_STATUS_FILLED", "FILLED", "2" -> OrderStatus.FILLED;
            case "ORDER_STATUS_REJECTED", "REJECTED", "3" -> OrderStatus.REJECTED;
            case "ORDER_STATUS_CANCELLED", "CANCELLED", "5" -> OrderStatus.CANCELLED;
            default -> OrderStatus.UNKNOWN;
        };
    }

    private String safeCode(CTraderAuthenticationException exception) {
        String message = exception.getMessage() == null ? "CTRADER_AUTHENTICATION_FAILED" : exception.getMessage();
        String normalized = message.replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(Locale.ROOT)
                .replaceAll("_+$", "");
        return "CTRADER_" + normalized.substring(0, Math.min(48, normalized.length()));
    }

    private boolean isProviderUncertain(CTraderAuthenticationException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("timeout") || message.contains("transport") || message.contains("submission failed")
                || message.contains("response is invalid") || message.contains("unavailable");
    }
}
