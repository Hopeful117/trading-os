package com.hope.trading.trading_core.execution.domain.aggregate;

import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class BrokerOrder {
    private final BrokerOrderId id;
    private final ExecutionIntentId intentId;
    private final ExecutionAttemptId attemptId;
    private final String externalOrderId;
    private final Instant createdAt;
    private BrokerOrderStatus status;
    private final List<Fill> fills;
    private Instant updatedAt;
    private long version;

    private BrokerOrder(BrokerOrderId id, ExecutionIntentId intentId,
            ExecutionAttemptId attemptId, String externalOrderId,
            BrokerOrderStatus status, List<Fill> fills, Instant createdAt,
            Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id); this.intentId = Objects.requireNonNull(intentId);
        this.attemptId = Objects.requireNonNull(attemptId);
        this.externalOrderId = required(externalOrderId);
        this.status = Objects.requireNonNull(status); this.fills = new ArrayList<>(fills);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt); this.version = version;
    }
    public static BrokerOrder acknowledged(BrokerOrderId id, ExecutionIntentId intentId,
            ExecutionAttemptId attemptId, String externalOrderId, Instant now) {
        return new BrokerOrder(id, intentId, attemptId, externalOrderId,
                BrokerOrderStatus.ACKNOWLEDGED, List.of(), now, now, 0);
    }
    public static BrokerOrder rejected(BrokerOrderId id, ExecutionIntentId intentId,
            ExecutionAttemptId attemptId, String externalOrderId, Instant now) {
        return new BrokerOrder(id, intentId, attemptId, externalOrderId,
                BrokerOrderStatus.REJECTED, List.of(), now, now, 0);
    }
    public static BrokerOrder rehydrate(BrokerOrderId id, ExecutionIntentId intentId,
            ExecutionAttemptId attemptId, String externalOrderId, BrokerOrderStatus status,
            List<Fill> fills, Instant createdAt, Instant updatedAt, long version) {
        return new BrokerOrder(id, intentId, attemptId, externalOrderId,
                status, fills, createdAt, updatedAt, version);
    }
    public void addFill(Fill fill, boolean complete, Instant now) {
        if (status != BrokerOrderStatus.ACKNOWLEDGED
                && status != BrokerOrderStatus.PARTIALLY_FILLED) {
            throw new InvalidExecutionStateException("Order cannot receive a fill in " + status);
        }
        fills.add(Objects.requireNonNull(fill));
        status = complete ? BrokerOrderStatus.FILLED : BrokerOrderStatus.PARTIALLY_FILLED;
        updatedAt = Objects.requireNonNull(now); version++;
    }
    public void cancel(Instant now) {
        if (status.terminal()) throw new InvalidExecutionStateException("Order is terminal");
        status = BrokerOrderStatus.CANCELLED; updatedAt = Objects.requireNonNull(now); version++;
    }
    private static String required(String value) {
        String result = Objects.requireNonNull(value).trim();
        if (result.isEmpty()) throw new IllegalArgumentException("external order id is required");
        return result;
    }
    public record Fill(String fillId, BigDecimal quantity, BigDecimal price,
                       BigDecimal fee, Instant executedAt) {
        public Fill {
            fillId = required(fillId);
            if (Objects.requireNonNull(quantity).signum() <= 0
                    || Objects.requireNonNull(price).signum() <= 0
                    || Objects.requireNonNull(fee).signum() < 0) {
                throw new IllegalArgumentException("invalid fill values");
            }
            Objects.requireNonNull(executedAt);
        }
    }
    public BrokerOrderId id() { return id; }
    public ExecutionIntentId intentId() { return intentId; }
    public ExecutionAttemptId attemptId() { return attemptId; }
    public String externalOrderId() { return externalOrderId; }
    public BrokerOrderStatus status() { return status; }
    public List<Fill> fills() { return List.copyOf(fills); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
