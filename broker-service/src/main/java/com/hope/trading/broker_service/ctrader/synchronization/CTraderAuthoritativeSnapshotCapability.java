package com.hope.trading.broker_service.ctrader.synchronization;

import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.AuthoritativeSnapshotCapability;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.connection.domain.BrokerProviderRegistrationCatalog;
import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import com.hope.trading.broker_service.ctrader.authentication.CTraderAuthenticationException;
import com.hope.trading.broker_service.ctrader.authentication.CTraderOpenApiClient;
import com.hope.trading.broker_service.ctrader.authentication.CTraderRequestSafetyGuard;
import com.hope.trading.broker_service.ctrader.authentication.CTraderToken;
import com.hope.trading.broker_service.ctrader.authentication.CTraderTokenCodec;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import com.hope.trading.broker_service.secret.application.SecretReader;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public final class CTraderAuthoritativeSnapshotCapability implements AuthoritativeSnapshotCapability {
    private static final Duration MAX_OBSERVATION_AGE = Duration.ofSeconds(30);

    private final BrokerConnectionRepository connections;
    private final BrokerProviderRegistrationCatalog providers;
    private final SecretReader secrets;
    private final CTraderOpenApiClient client;
    private final CTraderProperties properties;
    private final Clock clock;
    private final CTraderRequestSafetyGuard safety;

    public CTraderAuthoritativeSnapshotCapability(BrokerConnectionRepository connections,
                                                  BrokerProviderRegistrationCatalog providers,
                                                  SecretReader secrets,
                                                   CTraderOpenApiClient client,
                                                   CTraderProperties properties,
                                                   Clock clock,
                                                   CTraderRequestSafetyGuard safety) {
        this.connections = connections;
        this.providers = providers;
        this.secrets = secrets;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
        this.safety = safety;
    }

    @Override
    public BrokerAccountSnapshotV2 snapshot(UUID brokerAccountId) {
        var connection = connections.findByBrokerAccountId(brokerAccountId)
                .orElseThrow(() -> new CTraderAuthenticationException("cTrader connection is not available"));
        if (connection.provider() != BrokerProviderId.CTRADER
                || connection.externalAccountId() == null
                || connection.activeCredentialReference() == null) {
            throw new CTraderAuthenticationException("cTrader account is not authenticated and bound");
        }
        if (connection.providerEnvironment() == null
                || !connection.providerEnvironment().equalsIgnoreCase(properties.environment())) {
            throw new CTraderAuthenticationException("cTrader account environment does not match configuration");
        }
        if (connection.technicalStatus() == BrokerConnectionStatus.DISCONNECTED
                || connection.technicalStatus() == BrokerConnectionStatus.REVOKED
                || connection.technicalStatus() == BrokerConnectionStatus.REAUTHENTICATION_REQUIRED
                || connection.technicalStatus() == BrokerConnectionStatus.INVALID_CREDENTIALS
                || connection.technicalStatus() == BrokerConnectionStatus.INSUFFICIENT_PERMISSIONS
                || connection.technicalStatus() == BrokerConnectionStatus.TEMPORARILY_UNAVAILABLE) {
            throw new CTraderAuthenticationException("cTrader connection is not usable");
        }
        Instant now = clock.instant();
        try (CTraderRequestSafetyGuard.SnapshotLease ignored = safety.acquireSnapshot(connection.externalAccountId());
             PlainSecret secret = secrets.read(new CredentialReference(connection.activeCredentialReference()))) {
            CTraderToken token = CTraderTokenCodec.decode(secret.copyValue());
            if (!token.expiresAt().isAfter(now)) {
                throw new CTraderAuthenticationException("cTrader authentication has expired");
            }
            CTraderReadOnlyObservation observation = client.readOnlyObservation(token,
                    connection.externalAccountId(), now.minus(Duration.ofDays(7)), now);
            if (!connection.externalAccountId().equals(observation.externalAccountId())) {
                throw new CTraderAuthenticationException("cTrader external account identity mismatch");
            }
            return map(brokerAccountId, observation, now);
        }
    }

    private BrokerAccountSnapshotV2 map(UUID brokerAccountId, CTraderReadOnlyObservation observation, Instant now) {
        AccountFacts account = new AccountFacts(observation.valuationAsset(), observation.balance(),
                observation.equity(), observation.usedMargin(), observation.freeMargin());
        List<PositionSnapshot> positions = observation.positions().stream()
                .map(position -> new PositionSnapshot(position.instrument(), position.signedQuantity(),
                        position.entryPrice(), position.observedAt(), position.providerReference(),
                        position.margin(), position.stopLoss()))
                .toList();
        List<OrderSnapshot> orders = observation.orders().stream()
                .map(order -> new OrderSnapshot(order.providerReference(), order.clientReference(),
                        orderStatus(order.status()), order.quantity(), order.filledQuantity(), List.of(), order.observedAt()))
                .toList();
        List<ClosedTrade> deals = observation.deals().stream()
                .map(deal -> new ClosedTrade(deal.providerReference(), deal.instrument(), observation.valuationAsset(),
                        isSell(deal.side()) ? Side.SELL : Side.BUY, deal.quantity(), deal.price(),
                        deal.fee(), deal.realizedPnl(), deal.executedAt(), deal.swap()))
                .toList();
        SnapshotFreshness freshness = observation.completedAt().isBefore(now.minus(MAX_OBSERVATION_AGE))
                ? SnapshotFreshness.STALE : SnapshotFreshness.FRESH;
        SnapshotCompleteness completeness = observation.complete() && freshness == SnapshotFreshness.FRESH
                ? SnapshotCompleteness.COMPLETE : SnapshotCompleteness.INCOMPLETE;
        List<String> reasons = completeness == SnapshotCompleteness.COMPLETE
                ? observation.reasons() : appendReasons(observation, freshness);
        return new BrokerAccountSnapshotV2(brokerAccountId, providers.referenceFor(BrokerProviderId.CTRADER),
                observation.externalAccountId(),
                java.util.Map.of(observation.valuationAsset(), observation.balance()), positions,
                observation.providerObservedAt(), account, orders, deals, completeness, freshness,
                observation.startedAt(), observation.completedAt(), reasons);
    }

    private List<String> appendReasons(CTraderReadOnlyObservation observation, SnapshotFreshness freshness) {
        java.util.ArrayList<String> reasons = new java.util.ArrayList<>(observation.reasons());
        if (freshness == SnapshotFreshness.STALE) reasons.add("OBSERVATION_STALE");
        if (!observation.complete()) reasons.add("OBSERVATION_INCOMPLETE");
        return List.copyOf(new java.util.LinkedHashSet<>(reasons));
    }

    private OrderStatus orderStatus(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "ORDER_STATUS_ACCEPTED", "ACCEPTED", "1" -> OrderStatus.ACKNOWLEDGED;
            case "ORDER_STATUS_FILLED", "FILLED", "2" -> OrderStatus.FILLED;
            case "ORDER_STATUS_REJECTED", "REJECTED", "3" -> OrderStatus.REJECTED;
            case "ORDER_STATUS_CANCELLED", "CANCELLED", "5" -> OrderStatus.CANCELLED;
            case "ORDER_STATUS_EXPIRED", "EXPIRED", "4" -> OrderStatus.CANCELLED;
            default -> OrderStatus.UNKNOWN;
        };
    }

    private boolean isSell(String side) {
        return "SELL".equalsIgnoreCase(side) || "2".equals(side);
    }
}
