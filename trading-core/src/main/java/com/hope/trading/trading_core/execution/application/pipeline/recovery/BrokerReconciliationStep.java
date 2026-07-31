package com.hope.trading.trading_core.execution.application.pipeline.recovery;

import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.domain.service.RecoveryStrategyService.RecoveryStrategy;
import java.util.Objects;

public final class BrokerReconciliationStep {
    private final BrokerExecutionPort broker;
    public BrokerReconciliationStep(BrokerExecutionPort broker) {
        this.broker = Objects.requireNonNull(broker);
    }
    public void execute(RecoveryPipelineContext context) {
        if (context.strategy() != RecoveryStrategy.RECONCILE
                && context.strategy() != RecoveryStrategy.RESUME_RECONCILIATION) return;
        context.reconciliation(broker.reconcile(new BrokerExecutionPort.ReconciliationRequest(
                context.intent().id(), context.attempt().id(),
                context.intent().idempotencyKey(), context.intent().brokerAccountId())));
    }
}
