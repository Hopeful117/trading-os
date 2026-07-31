package com.hope.trading.trading_core.execution.application.pipeline;

import com.hope.trading.trading_core.execution.application.port.*;
import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import java.util.Objects;

public final class BrokerResponseProcessingStep {
    private final ExecutionIdGenerator ids;
    public BrokerResponseProcessingStep(ExecutionIdGenerator ids) {
        this.ids = Objects.requireNonNull(ids);
    }
    public void execute(ExecutionPipelineContext context) {
        switch (context.submissionResult()) {
            case BrokerExecutionPort.Acknowledged acknowledged ->
                    context.brokerOrder(BrokerOrder.acknowledged(
                            ids.nextBrokerOrderId(), context.intent().id(),
                            context.attempt().id(), acknowledged.externalOrderId(),
                            context.now()));
            case BrokerExecutionPort.Rejected rejected ->
                    context.brokerOrder(BrokerOrder.rejected(
                            ids.nextBrokerOrderId(), context.intent().id(),
                            context.attempt().id(),
                            rejected.externalOrderId() == null
                                    ? "rejected-" + context.attempt().id().value()
                                    : rejected.externalOrderId(),
                            context.now()));
            case BrokerExecutionPort.Unknown ignored -> { }
        }
    }
}
