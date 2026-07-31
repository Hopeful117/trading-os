package com.hope.trading.trading_core.execution.domain.service;

import com.hope.trading.trading_core.execution.domain.aggregate.*;
import com.hope.trading.trading_core.execution.domain.event.ExecutionEvent;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import java.time.Instant;

public final class ExecutionLifecycleService {
    public void validate(ExecutionIntent intent, Instant now) {
        intent.transition(ExecutionStatus.VALIDATED, now);
    }
    public void start(ExecutionIntent intent, ExecutionAttempt attempt, Instant now) {
        intent.activateAttempt(attempt.id(), now);
        intent.transition(ExecutionStatus.SUBMISSION_IN_PROGRESS, now);
        attempt.start(now);
        intent.addEvent(new ExecutionEvent.ExecutionAttemptStarted(
                intent.id(), attempt.id(), now));
        intent.addEvent(new ExecutionEvent.BrokerSubmissionStarted(
                intent.id(), attempt.id(), now));
    }
    public void acknowledged(ExecutionIntent intent, ExecutionAttempt attempt,
                             BrokerOrder order, String correlation, Instant now) {
        attempt.succeed(correlation, now);
        intent.clearActiveAttempt(attempt.id(), now);
        intent.transition(ExecutionStatus.COMPLETED, now);
        intent.addEvent(new ExecutionEvent.BrokerSubmissionAcknowledged(
                intent.id(), attempt.id(), correlation, now));
        intent.addEvent(new ExecutionEvent.ExecutionAttemptSucceeded(
                intent.id(), attempt.id(), now));
        intent.addEvent(new ExecutionEvent.BrokerOrderLinked(
                intent.id(), order.id(), now));
    }
    public void rejected(ExecutionIntent intent, ExecutionAttempt attempt,
                         BrokerOrder order, String code, Instant now) {
        attempt.fail(code, now); intent.clearActiveAttempt(attempt.id(), now);
        intent.transition(ExecutionStatus.FAILED, now);
        intent.addEvent(new ExecutionEvent.ExecutionAttemptFailed(
                intent.id(), attempt.id(), code, now));
        intent.addEvent(new ExecutionEvent.BrokerOrderRejected(
                intent.id(), order.id(), code, now));
    }
    public void unknown(ExecutionIntent intent, ExecutionAttempt attempt, Instant now) {
        attempt.markUnknown(now); intent.clearActiveAttempt(attempt.id(), now);
        intent.transition(ExecutionStatus.SUBMISSION_OUTCOME_UNKNOWN, now);
        intent.addEvent(new ExecutionEvent.ExecutionAttemptUnknown(
                intent.id(), attempt.id(), now));
    }
}
