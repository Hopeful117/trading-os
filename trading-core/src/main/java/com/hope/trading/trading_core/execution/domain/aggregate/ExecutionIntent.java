package com.hope.trading.trading_core.execution.domain.aggregate;

import com.hope.trading.trading_core.execution.domain.event.ExecutionEvent;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.model.*;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import java.time.Instant;
import java.util.*;

public final class ExecutionIntent {
    private final ExecutionIntentId id;
    private final TradePlanReference tradePlan;
    private final RiskApprovalReference riskApproval;
    private final IdempotencyKey idempotencyKey;
    private final UUID initiatorId;
    private final UUID brokerAccountId;
    private final ExecutionParameters parameters;
    private final Instant createdAt;
    private final Instant expiresAt;
    private ExecutionStatus status;
    private ExecutionAttemptId activeAttemptId;
    private Instant updatedAt;
    private long version;
    private final List<ExecutionEvent> events = new ArrayList<>();

    private ExecutionIntent(
            ExecutionIntentId id, TradePlanReference tradePlan,
            RiskApprovalReference riskApproval, IdempotencyKey idempotencyKey,
            UUID initiatorId, UUID brokerAccountId, ExecutionParameters parameters,
            ExecutionStatus status, ExecutionAttemptId activeAttemptId,
            Instant createdAt, Instant updatedAt, Instant expiresAt, long version
    ) {
        this.id = Objects.requireNonNull(id); this.tradePlan = Objects.requireNonNull(tradePlan);
        this.riskApproval = Objects.requireNonNull(riskApproval);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.initiatorId = Objects.requireNonNull(initiatorId);
        this.brokerAccountId = Objects.requireNonNull(brokerAccountId);
        this.parameters = Objects.requireNonNull(parameters);
        this.status = Objects.requireNonNull(status); this.activeAttemptId = activeAttemptId;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.version = version;
        if (!expiresAt.isAfter(createdAt)) throw new IllegalArgumentException("expiration must be after creation");
    }

    public static ExecutionIntent create(
            ExecutionIntentId id, TradePlanReference tradePlan,
            RiskApprovalReference approval, IdempotencyKey key, UUID initiatorId,
            UUID brokerAccountId, ExecutionParameters parameters,
            Instant now, Instant expiresAt
    ) {
        ExecutionIntent intent = new ExecutionIntent(id, tradePlan, approval, key,
                initiatorId, brokerAccountId, parameters, ExecutionStatus.CREATED,
                null, now, now, expiresAt, 0);
        intent.events.add(new ExecutionEvent.ExecutionIntentCreated(id, now));
        return intent;
    }

    public static ExecutionIntent rehydrate(
            ExecutionIntentId id, TradePlanReference tradePlan,
            RiskApprovalReference approval, IdempotencyKey key, UUID initiatorId,
            UUID brokerAccountId, ExecutionParameters parameters, ExecutionStatus status,
            ExecutionAttemptId activeAttemptId, Instant createdAt, Instant updatedAt,
            Instant expiresAt, long version
    ) {
        return new ExecutionIntent(id, tradePlan, approval, key, initiatorId,
                brokerAccountId, parameters, status, activeAttemptId, createdAt,
                updatedAt, expiresAt, version);
    }

    public void transition(ExecutionStatus target, Instant now) {
        if (!allowed(status, target)) {
            throw new InvalidExecutionStateException(
                    "Execution cannot transition from " + status + " to " + target);
        }
        status = target; updatedAt = Objects.requireNonNull(now); version++;
        if (target == ExecutionStatus.VALIDATED) {
            events.add(new ExecutionEvent.ExecutionIntentValidated(id, now));
        } else if (target == ExecutionStatus.CANCELLED) {
            events.add(new ExecutionEvent.ExecutionIntentCancelled(id, now));
        }
    }

    public void activateAttempt(ExecutionAttemptId attemptId, Instant now) {
        if (activeAttemptId != null) {
            throw new InvalidExecutionStateException("Only one active attempt is allowed");
        }
        activeAttemptId = Objects.requireNonNull(attemptId);
        updatedAt = Objects.requireNonNull(now); version++;
    }

    public void clearActiveAttempt(ExecutionAttemptId attemptId, Instant now) {
        if (!Objects.equals(activeAttemptId, attemptId)) {
            throw new InvalidExecutionStateException("Attempt is not active");
        }
        activeAttemptId = null; updatedAt = Objects.requireNonNull(now); version++;
    }

    public void addEvent(ExecutionEvent event) { events.add(Objects.requireNonNull(event)); }
    public List<ExecutionEvent> pullEvents() {
        List<ExecutionEvent> copy = List.copyOf(events); events.clear(); return copy;
    }

    private static boolean allowed(ExecutionStatus from, ExecutionStatus to) {
        return switch (from) {
            case CREATED -> to == ExecutionStatus.VALIDATED || to == ExecutionStatus.EXPIRED
                    || to == ExecutionStatus.CANCELLED;
            case VALIDATED -> to == ExecutionStatus.SUBMISSION_IN_PROGRESS
                    || to == ExecutionStatus.CANCELLED || to == ExecutionStatus.EXPIRED;
            case SUBMISSION_IN_PROGRESS -> to == ExecutionStatus.COMPLETED
                    || to == ExecutionStatus.FAILED
                    || to == ExecutionStatus.SUBMISSION_OUTCOME_UNKNOWN
                    || to == ExecutionStatus.RECONCILIATION_IN_PROGRESS;
            case SUBMISSION_OUTCOME_UNKNOWN -> to == ExecutionStatus.RECONCILIATION_IN_PROGRESS;
            case RECONCILIATION_IN_PROGRESS -> to == ExecutionStatus.COMPLETED
                    || to == ExecutionStatus.FAILED
                    || to == ExecutionStatus.RECOVERY_BLOCKED
                    || to == ExecutionStatus.VALIDATED;
            case FAILED -> to == ExecutionStatus.VALIDATED || to == ExecutionStatus.CANCELLED;
            case RECOVERY_BLOCKED -> to == ExecutionStatus.RECONCILIATION_IN_PROGRESS
                    || to == ExecutionStatus.CANCELLED;
            case COMPLETED, CANCELLED, EXPIRED -> false;
        };
    }

    public ExecutionIntentId id() { return id; }
    public TradePlanReference tradePlan() { return tradePlan; }
    public RiskApprovalReference riskApproval() { return riskApproval; }
    public IdempotencyKey idempotencyKey() { return idempotencyKey; }
    public UUID initiatorId() { return initiatorId; }
    public UUID brokerAccountId() { return brokerAccountId; }
    public ExecutionParameters parameters() { return parameters; }
    public ExecutionStatus status() { return status; }
    public Optional<ExecutionAttemptId> activeAttemptId() { return Optional.ofNullable(activeAttemptId); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant expiresAt() { return expiresAt; }
    public long version() { return version; }
}
