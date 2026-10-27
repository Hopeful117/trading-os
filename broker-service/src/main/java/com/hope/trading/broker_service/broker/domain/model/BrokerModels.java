package com.hope.trading.broker_service.broker.domain.model;

import com.hope.trading.broker_service.connection.domain.BrokerProviderId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class BrokerModels {
    private BrokerModels() {}

    private static final String INSTRUMENT = "instrument";

    public enum Side { BUY, SELL }
    public enum OrderType { MARKET, LIMIT }
    public enum OrderStatus { ACKNOWLEDGED, OPEN, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED, UNKNOWN }
    public enum SnapshotCompleteness { COMPLETE, INCOMPLETE }
    public enum SnapshotFreshness { FRESH, STALE, UNKNOWN }

    public record TechnicalCapabilities(UUID brokerAccountId, String provider, String instrument, long sourceVersion,
            Instant observedAt, List<OrderType> supportedOrderTypes,
            List<BigDecimal> supportedBuyLeverageLevels,
            List<BigDecimal> supportedSellLeverageLevels) {
            public TechnicalCapabilities {
            Objects.requireNonNull(brokerAccountId);
            provider = required(provider, "provider");
            instrument = required(instrument, INSTRUMENT);
            if (sourceVersion < 1) throw new IllegalArgumentException("sourceVersion must be positive");
            Objects.requireNonNull(observedAt);
            supportedOrderTypes = List.copyOf(supportedOrderTypes);
            supportedBuyLeverageLevels = validateLeverageLevels(supportedBuyLeverageLevels);
            supportedSellLeverageLevels = validateLeverageLevels(supportedSellLeverageLevels);
        }

        private static List<BigDecimal> validateLeverageLevels(List<BigDecimal> levels) {
            return levels.stream()
                    .map(level -> {
                        level = Objects.requireNonNull(level);
                        if (level.signum() <= 0) throw new IllegalArgumentException("leverage must be positive");
                        return level;
                    }).sorted().toList();
        }
    }

    public record MarginPreviewRequest(UUID brokerAccountId, String instrument, Side side,
            BigDecimal quantity, BigDecimal price, BigDecimal leverage) {
        public MarginPreviewRequest {
            Objects.requireNonNull(brokerAccountId);
            instrument = required(instrument, INSTRUMENT);
            Objects.requireNonNull(side);
            if (Objects.requireNonNull(quantity).signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
            if (Objects.requireNonNull(price).signum() <= 0) throw new IllegalArgumentException("price must be positive");
            if (leverage != null && leverage.signum() <= 0) throw new IllegalArgumentException("leverage must be positive");
        }
    }

    public record MarginPreview(UUID brokerAccountId, String instrument, BigDecimal amount,
            String currency, String sourceId, long sourceVersion, Instant observedAt) {
        public MarginPreview {
            Objects.requireNonNull(brokerAccountId);
            instrument = required(instrument, INSTRUMENT);
            if (Objects.requireNonNull(amount).signum() <= 0) throw new IllegalArgumentException("amount must be positive");
            currency = required(currency, "currency");
            sourceId = required(sourceId, "sourceId");
            if (sourceVersion < 1) throw new IllegalArgumentException("sourceVersion must be positive");
            Objects.requireNonNull(observedAt);
        }
    }

    public record ExecutionRequest(UUID executionIntentId, UUID executionAttemptId,
            String idempotencyKey, UUID brokerAccountId, String instrument, Side side,
            OrderType orderType, BigDecimal quantity, BigDecimal limitPrice,
            BigDecimal stopLossPrice) {
        public ExecutionRequest(UUID executionIntentId, UUID executionAttemptId,
                String idempotencyKey, UUID brokerAccountId, String instrument, Side side,
                OrderType orderType, BigDecimal quantity, BigDecimal limitPrice) {
            this(executionIntentId, executionAttemptId, idempotencyKey, brokerAccountId,
                    instrument, side, orderType, quantity, limitPrice, null);
        }
        public ExecutionRequest {
            Objects.requireNonNull(executionIntentId); Objects.requireNonNull(executionAttemptId);
            idempotencyKey = required(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(brokerAccountId); instrument = required(instrument, INSTRUMENT);
            Objects.requireNonNull(side); Objects.requireNonNull(orderType);
            if (Objects.requireNonNull(quantity).signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
            if (orderType == OrderType.LIMIT && (limitPrice == null || limitPrice.signum() <= 0))
                throw new IllegalArgumentException("limitPrice is required for a limit order");
            if (stopLossPrice != null && stopLossPrice.signum() <= 0)
                throw new IllegalArgumentException("stopLossPrice must be positive");
        }
    }
    public sealed interface ExecutionResult permits Acknowledged, Rejected, Unknown {}
    public record Acknowledged(String externalOrderId, String correlationId) implements ExecutionResult {
        public Acknowledged { externalOrderId=required(externalOrderId,"externalOrderId"); correlationId=required(correlationId,"correlationId"); }
    }
    public record Rejected(String externalOrderId, String reasonCode) implements ExecutionResult {
        public Rejected { reasonCode=required(reasonCode,"reasonCode"); }
    }
    public record Unknown(String reasonCode) implements ExecutionResult {
        public Unknown { reasonCode=required(reasonCode,"reasonCode"); }
    }
    public record ReconciliationRequest(UUID executionIntentId, UUID executionAttemptId,
            String idempotencyKey, UUID brokerAccountId) {
        public ReconciliationRequest { Objects.requireNonNull(executionIntentId); Objects.requireNonNull(executionAttemptId); idempotencyKey=required(idempotencyKey,"idempotencyKey"); Objects.requireNonNull(brokerAccountId); }
    }
    public sealed interface ReconciliationResult permits ReconciledOrder, ConfirmedAbsent, Inconsistent {}
    public record ReconciledOrder(String externalOrderId, String correlationId, OrderStatus status) implements ReconciliationResult {
        public ReconciledOrder { externalOrderId=required(externalOrderId,"externalOrderId"); correlationId=required(correlationId,"correlationId"); Objects.requireNonNull(status); }
    }
    public record ConfirmedAbsent() implements ReconciliationResult {}
    public record Inconsistent(String reasonCode) implements ReconciliationResult { public Inconsistent { reasonCode=required(reasonCode,"reasonCode"); } }
    public record AccountSnapshot(UUID brokerAccountId, java.util.Map<String,BigDecimal> balances, Instant observedAt) {
        public AccountSnapshot { Objects.requireNonNull(brokerAccountId); balances=java.util.Map.copyOf(balances); Objects.requireNonNull(observedAt); }
    }
    public record BrokerAccountSnapshotV2(UUID brokerAccountId, UUID providerRegistrationReference,
                                           String externalAccountId,
                                           java.util.Map<String,BigDecimal> balances,
                                           java.util.List<PositionSnapshot> positions,
                                           Instant observedAt,
                                           AccountFacts account,
                                           java.util.List<OrderSnapshot> orders,
                                           java.util.List<ClosedTrade> deals,
                                           SnapshotCompleteness completeness,
                                           SnapshotFreshness freshness,
                                           Instant observationStartedAt,
                                           Instant completedAt,
                                           java.util.List<String> unavailabilityReasons) {
        public BrokerAccountSnapshotV2 {
            Objects.requireNonNull(brokerAccountId);
            Objects.requireNonNull(providerRegistrationReference);
            balances = java.util.Map.copyOf(balances);
            positions = java.util.List.copyOf(positions);
            Objects.requireNonNull(observedAt);
            orders = java.util.List.copyOf(orders);
            deals = java.util.List.copyOf(deals);
            Objects.requireNonNull(completeness);
            Objects.requireNonNull(freshness);
            Objects.requireNonNull(observationStartedAt);
            Objects.requireNonNull(completedAt);
            unavailabilityReasons = List.copyOf(new java.util.LinkedHashSet<>(unavailabilityReasons));
        }

        public BrokerAccountSnapshotV2(UUID brokerAccountId, UUID providerRegistrationReference,
                                       String externalAccountId,
                                       java.util.Map<String,BigDecimal> balances,
                                       java.util.List<PositionSnapshot> positions,
                                       Instant observedAt) {
            this(brokerAccountId, providerRegistrationReference, externalAccountId, balances, positions,
                    observedAt, null, List.of(), List.of(), SnapshotCompleteness.COMPLETE,
                    SnapshotFreshness.FRESH, observedAt, observedAt, List.of());
        }

        public BrokerAccountSnapshotV2(UUID brokerAccountId, BrokerProviderId provider,
                                       String externalAccountId,
                                       java.util.Map<String, BigDecimal> balances,
                                       java.util.List<PositionSnapshot> positions,
                                       Instant observedAt) {
            this(brokerAccountId,
                    new com.hope.trading.broker_service.connection.domain.BrokerProviderRegistrationCatalog()
                            .referenceFor(provider),
                    externalAccountId, balances, positions, observedAt);
        }
    }
    public record AccountFacts(String valuationAsset, BigDecimal balance, BigDecimal equity,
                               BigDecimal usedMargin, BigDecimal freeMargin) {
        public AccountFacts {
            valuationAsset = required(valuationAsset, "valuationAsset");
            Objects.requireNonNull(balance);
        }
    }
    public record PositionSnapshot(String instrument, BigDecimal signedQuantity, BigDecimal entryPrice,
                                   Instant observedAt, String brokerPositionReference,
                                   BigDecimal margin, BigDecimal stopLoss) {
        public PositionSnapshot(String instrument, BigDecimal signedQuantity, BigDecimal entryPrice,
                                Instant observedAt, String brokerPositionReference) {
            this(instrument, signedQuantity, entryPrice, observedAt, brokerPositionReference, null, null);
        }
    }

    public record ResolveTargetRequest(UUID brokerAccountId, String brokerPositionReference) {
        public ResolveTargetRequest {
            Objects.requireNonNull(brokerAccountId);
            brokerPositionReference = required(brokerPositionReference, "brokerPositionReference");
        }
    }
    public record ResolvedPositionCloseTarget(UUID brokerAccountId, String resolvedMutationScope) {
        public ResolvedPositionCloseTarget {
            Objects.requireNonNull(brokerAccountId);
            resolvedMutationScope = required(resolvedMutationScope, "resolvedMutationScope");
        }
    }
    public record ExecuteCloseRequest(UUID brokerAccountId, String resolvedMutationScope, String idempotencyKey) {
        public ExecuteCloseRequest {
            Objects.requireNonNull(brokerAccountId);
            resolvedMutationScope = required(resolvedMutationScope, "resolvedMutationScope");
            idempotencyKey = required(idempotencyKey, "idempotencyKey");
        }
    }
    public sealed interface CloseResult permits CloseAcknowledged, CloseRejected, CloseUnknown {}
    public record CloseAcknowledged(String externalOrderId, String correlationId) implements CloseResult {
        public CloseAcknowledged { externalOrderId = required(externalOrderId, "externalOrderId"); correlationId = required(correlationId, "correlationId"); }
    }
    public record CloseRejected(String externalOrderId, String reasonCode) implements CloseResult {
        public CloseRejected { externalOrderId = required(externalOrderId, "externalOrderId"); reasonCode = required(reasonCode, "reasonCode"); }
    }
    public record CloseUnknown(String reasonCode) implements CloseResult {
        public CloseUnknown { reasonCode = required(reasonCode, "reasonCode"); }
    }
    public record ReconcileCloseRequest(UUID brokerAccountId, String resolvedMutationScope, String idempotencyKey) {
        public ReconcileCloseRequest {
            Objects.requireNonNull(brokerAccountId);
            resolvedMutationScope = required(resolvedMutationScope, "resolvedMutationScope");
            idempotencyKey = required(idempotencyKey, "idempotencyKey");
        }
    }
    public sealed interface ReconciliationCloseResult permits ExposureConfirmedAbsent, CommandConfirmedNotExecuted, Inconclusive {}
    public record ExposureConfirmedAbsent() implements ReconciliationCloseResult {}
    public record CommandConfirmedNotExecuted() implements ReconciliationCloseResult {}
    public record Inconclusive(String reasonCode) implements ReconciliationCloseResult { public Inconclusive { reasonCode = required(reasonCode, "reasonCode"); } }

    public record FillSnapshot(String fillId, BigDecimal quantity, BigDecimal price, BigDecimal fee, Instant executedAt) {}
    public record OrderSnapshot(String externalOrderId, String clientOrderId, OrderStatus status,
            BigDecimal quantity, BigDecimal filledQuantity, List<FillSnapshot> fills, Instant observedAt) {
        public OrderSnapshot { fills=List.copyOf(fills); }
    }
    public record RiskSnapshot(UUID brokerAccountId, long snapshotVersion, Instant observedAt,
            SnapshotCompleteness completeness, List<String> unavailabilityReasons,
            Map<String, BigDecimal> assetBalances, AccountRiskFacts account,
            List<RiskPosition> positions, List<ClosedTrade> closedTrades,
            List<LedgerEntry> ledgerEntries) {
        public RiskSnapshot {
            Objects.requireNonNull(brokerAccountId); Objects.requireNonNull(observedAt);
            Objects.requireNonNull(completeness); unavailabilityReasons=List.copyOf(new java.util.LinkedHashSet<>(unavailabilityReasons));
            assetBalances=Map.copyOf(assetBalances); positions=List.copyOf(positions);
            closedTrades=List.copyOf(closedTrades); ledgerEntries=List.copyOf(ledgerEntries);
        }
    }
    public record AccountRiskFacts(String valuationAsset, BigDecimal balance, BigDecimal equity,
            BigDecimal margin, BigDecimal startingBalance) {}
    public record RiskPosition(UUID positionId, String providerPositionReference,
            String providerReferenceProvenance, String instrument, BigDecimal signedQuantity,
            BigDecimal entryPrice, BigDecimal cost, BigDecimal marketValue,
            BigDecimal unrealizedPnl, BigDecimal margin, BigDecimal protectedQuantity,
            List<ProtectiveStop> protectiveStops) {
        public RiskPosition { protectiveStops=List.copyOf(protectiveStops); }
    }
    public record ProtectiveStop(String providerOrderReference,
            String providerReferenceProvenance, BigDecimal quantity, BigDecimal stopPrice) {}
    public record ClosedTrade(String providerTradeReference, String instrument, String settlementAsset, Side side,
            BigDecimal quantity, BigDecimal price, BigDecimal fee, BigDecimal realizedPnl,
            Instant closedAt, BigDecimal swap) {
        public ClosedTrade(String providerTradeReference, String instrument, String settlementAsset, Side side,
                           BigDecimal quantity, BigDecimal price, BigDecimal fee, BigDecimal realizedPnl,
                           Instant closedAt) {
            this(providerTradeReference, instrument, settlementAsset, side, quantity, price, fee,
                    realizedPnl, closedAt, null);
        }
    }
    public record LedgerEntry(String providerLedgerReference, String asset, String type,
            BigDecimal amount, BigDecimal fee, BigDecimal balance, Instant occurredAt) {}
    private static String required(String value,String name){String v=Objects.requireNonNull(value,name).trim();if(v.isEmpty())throw new IllegalArgumentException(name+" is required");return v;}
}
