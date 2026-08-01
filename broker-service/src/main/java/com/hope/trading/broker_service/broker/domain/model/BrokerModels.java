package com.hope.trading.broker_service.broker.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class BrokerModels {
    private BrokerModels() {}

    public enum Side { BUY, SELL }
    public enum OrderType { MARKET, LIMIT }
    public enum OrderStatus { ACKNOWLEDGED, OPEN, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED, UNKNOWN }

    public record ExecutionRequest(UUID executionIntentId, UUID executionAttemptId,
            String idempotencyKey, UUID brokerAccountId, String instrument, Side side,
            OrderType orderType, BigDecimal quantity, BigDecimal limitPrice) {
        public ExecutionRequest {
            Objects.requireNonNull(executionIntentId); Objects.requireNonNull(executionAttemptId);
            idempotencyKey = required(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(brokerAccountId); instrument = required(instrument, "instrument");
            Objects.requireNonNull(side); Objects.requireNonNull(orderType);
            if (Objects.requireNonNull(quantity).signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
            if (orderType == OrderType.LIMIT && (limitPrice == null || limitPrice.signum() <= 0))
                throw new IllegalArgumentException("limitPrice is required for a limit order");
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
    public record PositionSnapshot(String instrument, BigDecimal signedQuantity, BigDecimal entryPrice, Instant observedAt) {}
    public record FillSnapshot(String fillId, BigDecimal quantity, BigDecimal price, BigDecimal fee, Instant executedAt) {}
    public record OrderSnapshot(String externalOrderId, String clientOrderId, OrderStatus status,
            BigDecimal quantity, BigDecimal filledQuantity, List<FillSnapshot> fills, Instant observedAt) {
        public OrderSnapshot { fills=List.copyOf(fills); }
    }
    private static String required(String value,String name){String v=Objects.requireNonNull(value,name).trim();if(v.isEmpty())throw new IllegalArgumentException(name+" is required");return v;}
}
