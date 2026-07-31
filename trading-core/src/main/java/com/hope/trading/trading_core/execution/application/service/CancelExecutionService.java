package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.application.port.*;
import com.hope.trading.trading_core.execution.domain.aggregate.*;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.repository.*;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import java.time.Clock;
import java.util.Objects;

public final class CancelExecutionService {
    private final ExecutionIntentRepositoryPort intents;
    private final BrokerOrderRepositoryPort orders;
    private final BrokerExecutionPort broker;
    private final ExecutionEventPublisher events;
    private final ExecutionMetrics metrics;
    private final Clock clock;
    public CancelExecutionService(ExecutionIntentRepositoryPort intents,
            BrokerOrderRepositoryPort orders, BrokerExecutionPort broker,
            ExecutionEventPublisher events, ExecutionMetrics metrics, Clock clock) {
        this.intents = Objects.requireNonNull(intents); this.orders = Objects.requireNonNull(orders);
        this.broker = Objects.requireNonNull(broker); this.events = Objects.requireNonNull(events);
        this.metrics = Objects.requireNonNull(metrics); this.clock = Objects.requireNonNull(clock);
    }
    public ExecutionIntent cancel(ExecutionIntentId id) {
        ExecutionIntent intent = intents.findById(id).orElseThrow(
                () -> new InvalidExecutionStateException("Execution intent not found"));
        if (intent.status().terminal()) throw new InvalidExecutionStateException("Execution is terminal");
        orders.findByIntentId(id).ifPresent(order -> {
            if (!order.status().terminal()) {
                broker.cancel(intent.brokerAccountId(), order.externalOrderId());
                order.cancel(clock.instant()); orders.save(order);
            }
        });
        intent.transition(ExecutionStatus.CANCELLED, clock.instant());
        intents.save(intent); events.publish(intent.pullEvents()); metrics.executionCancelled();
        return intent;
    }
}
