package com.hope.trading.trading_core.execution.application.pipeline.recovery;

import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.domain.aggregate.*;
import com.hope.trading.trading_core.execution.domain.service.RecoveryStrategyService.RecoveryStrategy;
import java.time.Instant;
import java.util.*;

public final class RecoveryPipelineContext {
    private final ExecutionIntent intent;
    private final Instant now;
    private ExecutionAttempt attempt;
    private RecoveryStrategy strategy;
    private BrokerExecutionPort.ReconciliationResult reconciliation;
    public RecoveryPipelineContext(ExecutionIntent intent, Instant now) {
        this.intent = Objects.requireNonNull(intent); this.now = Objects.requireNonNull(now);
    }
    public ExecutionIntent intent() { return intent; }
    public Instant now() { return now; }
    public ExecutionAttempt attempt() { return attempt; }
    public void attempt(ExecutionAttempt value) { attempt = Objects.requireNonNull(value); }
    public RecoveryStrategy strategy() { return strategy; }
    public void strategy(RecoveryStrategy value) { strategy = Objects.requireNonNull(value); }
    public BrokerExecutionPort.ReconciliationResult reconciliation() { return reconciliation; }
    public void reconciliation(BrokerExecutionPort.ReconciliationResult value) {
        reconciliation = Objects.requireNonNull(value);
    }
}
