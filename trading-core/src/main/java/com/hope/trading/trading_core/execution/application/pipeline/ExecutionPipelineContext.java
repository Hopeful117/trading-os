package com.hope.trading.trading_core.execution.application.pipeline;

import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.domain.aggregate.*;
import java.time.Instant;
import java.util.Objects;

public final class ExecutionPipelineContext {
    private final ExecutionIntent intent;
    private final Instant now;
    private ExecutionAttempt attempt;
    private BrokerExecutionPort.SubmissionResult submissionResult;
    private BrokerOrder brokerOrder;
    public ExecutionPipelineContext(ExecutionIntent intent, Instant now) {
        this.intent = Objects.requireNonNull(intent); this.now = Objects.requireNonNull(now);
    }
    public ExecutionIntent intent() { return intent; }
    public Instant now() { return now; }
    public ExecutionAttempt attempt() { return attempt; }
    public void attempt(ExecutionAttempt value) { attempt = Objects.requireNonNull(value); }
    public BrokerExecutionPort.SubmissionResult submissionResult() { return submissionResult; }
    public void submissionResult(BrokerExecutionPort.SubmissionResult value) {
        submissionResult = Objects.requireNonNull(value);
    }
    public BrokerOrder brokerOrder() { return brokerOrder; }
    public void brokerOrder(BrokerOrder value) { brokerOrder = Objects.requireNonNull(value); }
}
