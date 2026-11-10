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
import com.hope.trading.broker_service.ctrader.experimentation.CTraderDerivedEquityExperiment;
import com.hope.trading.broker_service.secret.application.SecretReader;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
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
        CTraderReadOnlyObservation.AccountFacts providerFacts = observation.accountFacts();
        Instant capturedAt = observation.completedAt();
        var derived = CTraderDerivedEquityExperiment.evaluate(observation, capturedAt,
                MAX_OBSERVATION_AGE, Duration.ofSeconds(30));
        DerivedEquityFact derivedEquityFact = derived.candidateEquity() == null ? null
                : new DerivedEquityFact(derived.candidateEquity(), derived.valuationAsset(), "BROKER_DERIVED",
                derived.calculationVersion(), derived.hypothesis().name(), derived.consistencyStatus().name(),
                derived.validationStatus().name(), derived.providerObservedAt(), derived.completedAt(),
                derived.diagnostics());
        EquityFact equityFact = providerEquityFact(observation, providerFacts, capturedAt);
        if (equityFact.availability() == EquityAvailability.UNAVAILABLE) {
            equityFact = emptyAccountDerivedEquityFact(observation, providerFacts, capturedAt);
            if (equityFact.availability() == EquityAvailability.AVAILABLE) {
                derivedEquityFact = new DerivedEquityFact(equityFact.value(), equityFact.currency(),
                        equityFact.provenance().name(), "CTRADER_EMPTY_ACCOUNT_EQUITY_V1",
                        "BALANCE_WHEN_NO_OPEN_POSITIONS", "CONSISTENT_WITH_OBSERVATIONS", "VALIDATED",
                        equityFact.observedAt(), equityFact.capturedAt(), List.of());
            }
        }
        BigDecimal accountEquity = equityFact.availability() == EquityAvailability.AVAILABLE
                ? equityFact.value() : observation.equity();
        AccountFacts account = new AccountFacts(observation.valuationAsset(), observation.balance(),
                accountEquity, observation.usedMargin(), observation.freeMargin(),
                accountPositionModel(providerFacts.accountType()),
                marginCalculationModel(providerFacts.totalMarginCalculationType()),
                providerFacts.moneyDigits().present() ? providerFacts.moneyDigits().value() : null,
                leverage(providerFacts.leverageInCents()),
                equityFact, derivedEquityFact, new BonusFacts(
                        value(providerFacts.managerBonus()), value(providerFacts.ibBonus()),
                        value(providerFacts.nonWithdrawableBonus())));
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
                .map(deal -> new ClosedTrade(deal.providerReference(), deal.providerPositionReference(),
                        deal.instrument(), observation.valuationAsset(), isSell(deal.side()) ? Side.SELL : Side.BUY,
                        deal.quantity(), deal.price(), deal.fee(), deal.realizedPnl(), deal.executedAt(),
                         deal.swap(), deal.closingPosition()))
                .toList();
        List<PositionPnlSnapshot> positionPnls = observation.pnlSet().values().stream()
                .map(pnl -> new PositionPnlSnapshot(pnl.providerPositionReference(),
                        value(pnl.grossUnrealizedPnl()), value(pnl.netUnrealizedPnl()),
                        pnl.moneyDigits().present() ? pnl.moneyDigits().value() : null))
                .toList();
        SnapshotFreshness freshness = observation.providerObservedAt().isBefore(now.minus(MAX_OBSERVATION_AGE))
                ? SnapshotFreshness.STALE : SnapshotFreshness.FRESH;
        boolean accountFactsComplete = observation.valuationAsset() != null
                 && !observation.valuationAsset().isBlank()
                 && observation.balance() != null
                 && equityFact.availability() == EquityAvailability.AVAILABLE
                 && observation.usedMargin() != null
                 && observation.freeMargin() != null;
        boolean pnlFactsComplete = observation.pnlComplete()
                && observation.pnlSet().availability() != CTraderReadOnlyObservation.CollectionAvailability.UNAVAILABLE_OR_ABSENT;
        SnapshotCompleteness completeness = observation.complete() && accountFactsComplete && pnlFactsComplete
                && freshness == SnapshotFreshness.FRESH
                ? SnapshotCompleteness.COMPLETE : SnapshotCompleteness.INCOMPLETE;
        List<String> reasons = completeness == SnapshotCompleteness.COMPLETE
                ? observation.reasons() : appendReasons(observation, freshness);
        if (equityFact.availability() == EquityAvailability.UNAVAILABLE
                || observation.usedMargin() == null || observation.freeMargin() == null) {
            java.util.ArrayList<String> incompleteReasons = new java.util.ArrayList<>(reasons);
            if (equityFact.availability() == EquityAvailability.UNAVAILABLE) incompleteReasons.add("EQUITY_UNAVAILABLE");
            if (observation.usedMargin() == null) incompleteReasons.add("USED_MARGIN_UNAVAILABLE");
            if (observation.freeMargin() == null) incompleteReasons.add("FREE_MARGIN_UNAVAILABLE");
            reasons = List.copyOf(new java.util.LinkedHashSet<>(incompleteReasons));
        }
        if (!pnlFactsComplete) {
            java.util.ArrayList<String> incompleteReasons = new java.util.ArrayList<>(reasons);
            incompleteReasons.add("UNREALIZED_PNL_UNAVAILABLE");
            reasons = List.copyOf(new java.util.LinkedHashSet<>(incompleteReasons));
        }
        return new BrokerAccountSnapshotV2(brokerAccountId, providers.referenceFor(BrokerProviderId.CTRADER),
                observation.externalAccountId(),
                java.util.Map.of(observation.valuationAsset(), observation.balance()), positions,
                observation.providerObservedAt(), account, orders, deals, positionPnls,
                 collectionAvailability(observation.positionSet().availability()),
                 collectionAvailability(observation.orderSet().availability()),
                 collectionAvailability(observation.pnlSet().availability()),
                 collectionAvailability(observation.dealSet().availability()), observation.pnlComplete(), completeness, freshness,
                 observation.startedAt(), observation.completedAt(), reasons);
    }

    private EquityFact providerEquityFact(CTraderReadOnlyObservation observation,
                                          CTraderReadOnlyObservation.AccountFacts providerFacts,
                                          Instant capturedAt) {
        boolean usable = observation.equity() != null
                && observation.equity().signum() >= 0
                && observation.valuationAsset() != null
                && !observation.valuationAsset().isBlank()
                && providerFacts.depositAssetId().present()
                && providerFacts.moneyDigits().present()
                && observation.providerObservedAt() != null
                && !observation.providerObservedAt().isAfter(capturedAt)
                && !observation.providerObservedAt().isBefore(capturedAt.minus(MAX_OBSERVATION_AGE));
        if (!usable) return EquityFact.unavailable("EQUITY_PROVIDER_SOURCE_UNVALIDATED");
        return new EquityFact(observation.equity(), observation.valuationAsset(), EquityProvenance.BROKER_REPORTED,
                EquityAvailability.AVAILABLE, observation.providerObservedAt(), capturedAt, true, true, true,
                "CTRADER_TRADER_ACCOUNT", "TRADER_RESPONSE_EQUITY", List.of());
    }

    private EquityFact emptyAccountDerivedEquityFact(CTraderReadOnlyObservation observation,
                                                     CTraderReadOnlyObservation.AccountFacts providerFacts,
                                                     Instant capturedAt) {
        List<String> rejectionReasons = new java.util.ArrayList<>();
        boolean noOpenPositions = observation.positionSet().availability()
                == CTraderReadOnlyObservation.CollectionAvailability.AVAILABLE_EMPTY
                && observation.positionSet().values().isEmpty();
        boolean noPositionPnl = observation.pnlSet().availability()
                == CTraderReadOnlyObservation.CollectionAvailability.AVAILABLE_EMPTY
                && observation.pnlSet().values().isEmpty();
        boolean inactiveBonuses = inactiveBonus(providerFacts.managerBonus())
                && inactiveBonus(providerFacts.ibBonus())
                && inactiveBonus(providerFacts.nonWithdrawableBonus());
        if (!observation.complete()) rejectionReasons.add("EMPTY_ACCOUNT_OBSERVATION_INCOMPLETE");
        if (!observation.pnlComplete()) rejectionReasons.add("EMPTY_ACCOUNT_PNL_INCOMPLETE");
        if (!noOpenPositions) rejectionReasons.add("EMPTY_ACCOUNT_POSITIONS_NOT_EMPTY");
        if (!noPositionPnl) rejectionReasons.add("EMPTY_ACCOUNT_POSITION_PNL_NOT_EMPTY");
        if (!inactiveBonuses) rejectionReasons.add("EMPTY_ACCOUNT_BONUS_ACTIVE");
        if (observation.balance() == null || observation.balance().signum() < 0) {
            rejectionReasons.add("EMPTY_ACCOUNT_BALANCE_INVALID");
        }
        if (observation.valuationAsset() == null || observation.valuationAsset().isBlank()) {
            rejectionReasons.add("EMPTY_ACCOUNT_VALUATION_ASSET_INVALID");
        }
        if (observation.providerObservedAt() == null
                || observation.providerObservedAt().isAfter(capturedAt)
                || observation.providerObservedAt().isBefore(capturedAt.minus(MAX_OBSERVATION_AGE))) {
            rejectionReasons.add("EMPTY_ACCOUNT_OBSERVATION_TIMESTAMP_INVALID");
        }
        if (!rejectionReasons.isEmpty()) return EquityFact.unavailable(rejectionReasons);
        return new EquityFact(observation.balance(), observation.valuationAsset(), EquityProvenance.BROKER_DERIVED,
                EquityAvailability.AVAILABLE, observation.providerObservedAt(), capturedAt, true, true, true,
                "CTRADER_EMPTY_ACCOUNT", "BALANCE_WHEN_NO_OPEN_POSITIONS", List.of());
    }

    private boolean inactiveBonus(CTraderReadOnlyObservation.ProviderField<BigDecimal> bonus) {
        return !bonus.present() || bonus.value().signum() == 0;
    }

    private BigDecimal value(CTraderReadOnlyObservation.ProviderField<BigDecimal> field) {
        return field.present() ? field.value() : null;
    }

    private BigDecimal leverage(CTraderReadOnlyObservation.ProviderField<Long> field) {
        return field.present() ? BigDecimal.valueOf(field.value()).movePointLeft(2) : null;
    }

    private AccountPositionModel accountPositionModel(CTraderReadOnlyObservation.ProviderField<String> field) {
        if (!field.present()) return AccountPositionModel.UNKNOWN;
        try {
            return AccountPositionModel.valueOf(field.value());
        } catch (IllegalArgumentException exception) {
            return AccountPositionModel.UNKNOWN;
        }
    }

    private MarginCalculationModel marginCalculationModel(CTraderReadOnlyObservation.ProviderField<String> field) {
        if (!field.present()) return MarginCalculationModel.UNKNOWN;
        try {
            return MarginCalculationModel.valueOf(field.value());
        } catch (IllegalArgumentException exception) {
            return MarginCalculationModel.UNKNOWN;
        }
    }

    private SnapshotCollectionAvailability collectionAvailability(
            CTraderReadOnlyObservation.CollectionAvailability availability) {
        return switch (availability) {
            case AVAILABLE_EMPTY -> SnapshotCollectionAvailability.AVAILABLE_EMPTY;
            case AVAILABLE_NON_EMPTY -> SnapshotCollectionAvailability.AVAILABLE_NON_EMPTY;
            case TRUNCATED -> SnapshotCollectionAvailability.TRUNCATED;
            case UNAVAILABLE_OR_ABSENT -> SnapshotCollectionAvailability.UNAVAILABLE_OR_ABSENT;
        };
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
