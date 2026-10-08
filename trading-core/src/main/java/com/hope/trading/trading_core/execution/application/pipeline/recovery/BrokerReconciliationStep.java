package com.hope.trading.trading_core.execution.application.pipeline.recovery;

import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.execution.domain.service.RecoveryStrategyService.RecoveryStrategy;
import java.util.Objects;

public final class BrokerReconciliationStep {
    private final BrokerExecutionPort broker;
    private final BrokerAccountRepository brokerAccounts;
    public BrokerReconciliationStep(BrokerExecutionPort broker, BrokerAccountRepository brokerAccounts) {
        this.broker = Objects.requireNonNull(broker);
        this.brokerAccounts = Objects.requireNonNull(brokerAccounts);
    }
    public void execute(RecoveryPipelineContext context) {
        if (context.strategy() != RecoveryStrategy.RECONCILE
                && context.strategy() != RecoveryStrategy.RESUME_RECONCILIATION) return;
        var brokerAccount = brokerAccounts.findById(context.intent().brokerAccountId())
                .orElseThrow(() -> new IllegalStateException("Broker account not found for recovery"));
        if (brokerAccount.executionMode() == ExecutionMode.PAPER) {
            throw new IllegalStateException("PAPER execution cannot use broker reconciliation");
        }
        context.reconciliation(broker.reconcile(new BrokerExecutionPort.ReconciliationRequest(
                context.intent().id(), context.attempt().id(),
                context.intent().idempotencyKey(), context.intent().brokerAccountId())));
    }
}
