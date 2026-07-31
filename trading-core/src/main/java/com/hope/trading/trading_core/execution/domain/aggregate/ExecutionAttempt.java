package com.hope.trading.trading_core.execution.domain.aggregate;

import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import java.time.Instant;
import java.util.Objects;

public final class ExecutionAttempt {
    private final ExecutionAttemptId id;
    private final ExecutionIntentId intentId;
    private final int attemptNumber;
    private final Instant createdAt;
    private AttemptStatus status;
    private String brokerCorrelationId;
    private String resultCode;
    private Instant startedAt;
    private Instant completedAt;
    private long version;

    private ExecutionAttempt(ExecutionAttemptId id, ExecutionIntentId intentId,
            int attemptNumber, AttemptStatus status, String brokerCorrelationId,
            String resultCode, Instant createdAt, Instant startedAt,
            Instant completedAt, long version) {
        this.id = Objects.requireNonNull(id); this.intentId = Objects.requireNonNull(intentId);
        if (attemptNumber < 1) throw new IllegalArgumentException("attempt number starts at 1");
        this.attemptNumber = attemptNumber; this.status = Objects.requireNonNull(status);
        this.brokerCorrelationId = brokerCorrelationId; this.resultCode = resultCode;
        this.createdAt = Objects.requireNonNull(createdAt); this.startedAt = startedAt;
        this.completedAt = completedAt; this.version = version;
    }
    public static ExecutionAttempt create(ExecutionAttemptId id, ExecutionIntentId intentId,
                                          int number, Instant now) {
        return new ExecutionAttempt(id, intentId, number, AttemptStatus.CREATED,
                null, null, now, null, null, 0);
    }
    public static ExecutionAttempt rehydrate(ExecutionAttemptId id, ExecutionIntentId intentId,
            int number, AttemptStatus status, String correlation, String result,
            Instant created, Instant started, Instant completed, long version) {
        return new ExecutionAttempt(id, intentId, number, status, correlation,
                result, created, started, completed, version);
    }
    public void start(Instant now) {
        require(AttemptStatus.CREATED); status = AttemptStatus.STARTED;
        startedAt = Objects.requireNonNull(now); version++;
    }
    public void succeed(String correlation, Instant now) {
        require(AttemptStatus.STARTED); status = AttemptStatus.SUCCEEDED;
        brokerCorrelationId = required(correlation); resultCode = "ACKNOWLEDGED";
        completedAt = Objects.requireNonNull(now); version++;
    }
    public void fail(String code, Instant now) {
        require(AttemptStatus.STARTED); status = AttemptStatus.FAILED;
        resultCode = required(code); completedAt = Objects.requireNonNull(now); version++;
    }
    public void timeout(Instant now) {
        require(AttemptStatus.STARTED); status = AttemptStatus.TIMED_OUT;
        resultCode = "TIMEOUT"; completedAt = Objects.requireNonNull(now); version++;
    }
    public void markUnknown(Instant now) {
        if (status != AttemptStatus.STARTED && status != AttemptStatus.TIMED_OUT) {
            throw new InvalidExecutionStateException("Only submitted attempts may become unknown");
        }
        status = AttemptStatus.OUTCOME_UNKNOWN; resultCode = "OUTCOME_UNKNOWN";
        completedAt = Objects.requireNonNull(now); version++;
    }
    public void reconcile(String correlation, String code, Instant now) {
        require(AttemptStatus.OUTCOME_UNKNOWN); status = AttemptStatus.RECONCILED;
        brokerCorrelationId = correlation; resultCode = required(code);
        completedAt = Objects.requireNonNull(now); version++;
    }
    private void require(AttemptStatus expected) {
        if (status != expected) throw new InvalidExecutionStateException(
                "Attempt must be " + expected + " but is " + status);
    }
    private String required(String value) {
        String result = Objects.requireNonNull(value).trim();
        if (result.isEmpty()) throw new IllegalArgumentException("value is required");
        return result;
    }
    public ExecutionAttemptId id() { return id; }
    public ExecutionIntentId intentId() { return intentId; }
    public int attemptNumber() { return attemptNumber; }
    public AttemptStatus status() { return status; }
    public String brokerCorrelationId() { return brokerCorrelationId; }
    public String resultCode() { return resultCode; }
    public Instant createdAt() { return createdAt; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public long version() { return version; }
}
