package com.hope.trading.market_intelligence.domain.capability;

import java.time.Instant;
import java.util.*;

public final class CapabilityExecution {
    private static final Map<CapabilityExecutionState, Set<CapabilityExecutionState>> TRANSITIONS =
            Map.of(
                    CapabilityExecutionState.CREATED, EnumSet.of(
                            CapabilityExecutionState.WAITING_FOR_REQUIREMENTS,
                            CapabilityExecutionState.READY, CapabilityExecutionState.SKIPPED,
                            CapabilityExecutionState.CANCELLED),
                    CapabilityExecutionState.WAITING_FOR_REQUIREMENTS, EnumSet.of(
                            CapabilityExecutionState.READY, CapabilityExecutionState.SKIPPED,
                            CapabilityExecutionState.FAILED, CapabilityExecutionState.CANCELLED,
                            CapabilityExecutionState.TIMED_OUT),
                    CapabilityExecutionState.READY, EnumSet.of(
                            CapabilityExecutionState.RUNNING, CapabilityExecutionState.SKIPPED,
                            CapabilityExecutionState.CANCELLED),
                    CapabilityExecutionState.RUNNING, EnumSet.of(
                            CapabilityExecutionState.COMPLETED, CapabilityExecutionState.FAILED,
                            CapabilityExecutionState.CANCELLED, CapabilityExecutionState.TIMED_OUT)
            );

    private final UUID id;
    private final UUID analysisExecutionId;
    private final CapabilityId capabilityId;
    private final CapabilityVersion capabilityVersion;
    private final CapabilityExecutionState state;
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant completedAt;
    private final CapabilityResult result;
    private final CapabilityFailure failure;
    private final SkipReason skipReason;
    private final UUID executionGroupId;
    private final int attemptNumber;
    private final UUID previousAttemptId;

    private CapabilityExecution(
            UUID id, UUID analysisExecutionId, CapabilityId capabilityId,
            CapabilityVersion capabilityVersion, CapabilityExecutionState state,
            Instant createdAt, Instant startedAt, Instant completedAt,
            CapabilityResult result, CapabilityFailure failure, SkipReason skipReason,
            UUID executionGroupId, int attemptNumber, UUID previousAttemptId) {
        this.id = Objects.requireNonNull(id);
        this.analysisExecutionId = Objects.requireNonNull(analysisExecutionId);
        this.capabilityId = Objects.requireNonNull(capabilityId);
        this.capabilityVersion = Objects.requireNonNull(capabilityVersion);
        this.state = Objects.requireNonNull(state);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.result = result;
        this.failure = failure;
        this.skipReason = skipReason;
        this.executionGroupId = Objects.requireNonNull(executionGroupId);
        if (attemptNumber < 1) throw new IllegalArgumentException("Attempt starts at 1");
        this.attemptNumber = attemptNumber;
        this.previousAttemptId = previousAttemptId;
    }

    public static CapabilityExecution created(
            UUID analysisExecutionId, CapabilityMetadata metadata, Instant at) {
        return new CapabilityExecution(
                UUID.randomUUID(), analysisExecutionId, metadata.id(), metadata.version(),
                CapabilityExecutionState.CREATED, at, null, null, null, null, null,
                UUID.randomUUID(), 1, null);
    }

    public static CapabilityExecution retryFrom(CapabilityExecution previous, Instant at) {
        if (previous.state != CapabilityExecutionState.FAILED
                && previous.state != CapabilityExecutionState.TIMED_OUT) {
            throw new IllegalStateException("Only failed or timed out executions can be retried");
        }
        return new CapabilityExecution(
                UUID.randomUUID(), previous.analysisExecutionId, previous.capabilityId,
                previous.capabilityVersion, CapabilityExecutionState.CREATED, at,
                null, null, null, null, null, previous.executionGroupId,
                previous.attemptNumber + 1, previous.id);
    }

    public CapabilityExecution transitionTo(CapabilityExecutionState target, Instant at) {
        if (state.isTerminal() || !TRANSITIONS.getOrDefault(state, Set.of()).contains(target)) {
            throw new IllegalCapabilityExecutionTransitionException(state, target);
        }
        return copy(target, target == CapabilityExecutionState.RUNNING ? at : startedAt,
                target.isTerminal() ? at : null, result, failure, skipReason);
    }

    public CapabilityExecution complete(CapabilityResult accepted, Instant at) {
        CapabilityExecution next = transitionTo(CapabilityExecutionState.COMPLETED, at);
        return next.copy(next.state, startedAt, at, Objects.requireNonNull(accepted), null, null);
    }
    public CapabilityExecution fail(CapabilityFailure accepted, Instant at) {
        CapabilityExecution next = transitionTo(CapabilityExecutionState.FAILED, at);
        return next.copy(next.state, startedAt, at, null, Objects.requireNonNull(accepted), null);
    }
    public CapabilityExecution skip(SkipReason reason, Instant at) {
        CapabilityExecution next = transitionTo(CapabilityExecutionState.SKIPPED, at);
        return next.copy(next.state, startedAt, at, null, null, Objects.requireNonNull(reason));
    }

    private CapabilityExecution copy(
            CapabilityExecutionState nextState, Instant nextStarted, Instant nextCompleted,
            CapabilityResult nextResult, CapabilityFailure nextFailure, SkipReason nextSkip) {
        return new CapabilityExecution(
                id, analysisExecutionId, capabilityId, capabilityVersion, nextState,
                createdAt, nextStarted, nextCompleted, nextResult, nextFailure, nextSkip,
                executionGroupId, attemptNumber, previousAttemptId);
    }

    public UUID id() { return id; }
    public UUID analysisExecutionId() { return analysisExecutionId; }
    public CapabilityId capabilityId() { return capabilityId; }
    public CapabilityVersion capabilityVersion() { return capabilityVersion; }
    public CapabilityExecutionState state() { return state; }
    public Instant createdAt() { return createdAt; }
    public Optional<Instant> startedAt() { return Optional.ofNullable(startedAt); }
    public Optional<Instant> completedAt() { return Optional.ofNullable(completedAt); }
    public Optional<CapabilityResult> result() { return Optional.ofNullable(result); }
    public Optional<CapabilityFailure> failure() { return Optional.ofNullable(failure); }
    public Optional<SkipReason> skipReason() { return Optional.ofNullable(skipReason); }
    public UUID executionGroupId() { return executionGroupId; }
    public int attemptNumber() { return attemptNumber; }
    public Optional<UUID> previousAttemptId() { return Optional.ofNullable(previousAttemptId); }
}
