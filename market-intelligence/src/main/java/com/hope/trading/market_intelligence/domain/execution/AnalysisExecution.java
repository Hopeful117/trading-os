package com.hope.trading.market_intelligence.domain.execution;

import com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence;

import java.time.Instant;
import java.util.*;

/**
 * Technical aggregate governing one asynchronous analysis.
 *
 * <p>All changes return a new instance. Terminal executions and accepted
 * consolidated results are immutable.</p>
 */
public final class AnalysisExecution {
    private static final Map<AnalysisExecutionStatus, Set<AnalysisExecutionStatus>> TRANSITIONS =
            Map.of(
                    AnalysisExecutionStatus.REQUESTED, EnumSet.of(
                            AnalysisExecutionStatus.ACCEPTED,
                            AnalysisExecutionStatus.FAILED,
                            AnalysisExecutionStatus.CANCELLED,
                            AnalysisExecutionStatus.EXPIRED
                    ),
                    AnalysisExecutionStatus.ACCEPTED, EnumSet.of(
                            AnalysisExecutionStatus.CONTEXT_BUILDING,
                            AnalysisExecutionStatus.FAILED,
                            AnalysisExecutionStatus.CANCELLED,
                            AnalysisExecutionStatus.EXPIRED
                    ),
                    AnalysisExecutionStatus.CONTEXT_BUILDING, EnumSet.of(
                            AnalysisExecutionStatus.RUNNING,
                            AnalysisExecutionStatus.FAILED,
                            AnalysisExecutionStatus.CANCELLED,
                            AnalysisExecutionStatus.EXPIRED
                    ),
                    AnalysisExecutionStatus.RUNNING, EnumSet.of(
                            AnalysisExecutionStatus.PARTIALLY_COMPLETED,
                            AnalysisExecutionStatus.COMPLETED,
                            AnalysisExecutionStatus.FAILED,
                            AnalysisExecutionStatus.CANCELLED,
                            AnalysisExecutionStatus.EXPIRED
                    ),
                    AnalysisExecutionStatus.PARTIALLY_COMPLETED, EnumSet.of(
                            AnalysisExecutionStatus.RUNNING,
                            AnalysisExecutionStatus.COMPLETED,
                            AnalysisExecutionStatus.FAILED,
                            AnalysisExecutionStatus.CANCELLED,
                            AnalysisExecutionStatus.EXPIRED
                    )
            );

    private final UUID executionId;
    private final IdempotencyKey idempotencyKey;
    private final AnalysisExecutionStatus status;
    private final AnalysisResultQuality resultQuality;
    private final AnalysisExecutionPolicy executionPolicy;
    private final Instant requestedAt;
    private final Instant updatedAt;
    private final Instant expiresAt;
    private final Instant completedAt;
    private final List<String> capabilities;
    private final RetryMetadata retryMetadata;
    private final AnalysisExecutionProvenance provenance;
    private final AnalysisTraceMetadata traceMetadata;
    private final ConsolidatedIntelligence result;

    private AnalysisExecution(
            UUID executionId,
            IdempotencyKey idempotencyKey,
            AnalysisExecutionStatus status,
            AnalysisResultQuality resultQuality,
            AnalysisExecutionPolicy executionPolicy,
            Instant requestedAt,
            Instant updatedAt,
            Instant expiresAt,
            Instant completedAt,
            List<String> capabilities,
            RetryMetadata retryMetadata,
            AnalysisExecutionProvenance provenance,
            AnalysisTraceMetadata traceMetadata,
            ConsolidatedIntelligence result
    ) {
        this.executionId = Objects.requireNonNull(executionId);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.status = Objects.requireNonNull(status);
        this.resultQuality = resultQuality;
        this.executionPolicy = Objects.requireNonNull(executionPolicy);
        this.requestedAt = Objects.requireNonNull(requestedAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.completedAt = completedAt;
        this.capabilities = List.copyOf(capabilities);
        this.retryMetadata = Objects.requireNonNull(retryMetadata);
        this.provenance = Objects.requireNonNull(provenance);
        this.traceMetadata = Objects.requireNonNull(traceMetadata);
        this.result = result;
    }

    public static AnalysisExecution requested(
            UUID executionId,
            IdempotencyKey idempotencyKey,
            AnalysisExecutionPolicy policy,
            Instant requestedAt,
            List<String> capabilities,
            AnalysisExecutionProvenance provenance,
            AnalysisTraceMetadata traceMetadata
    ) {
        return new AnalysisExecution(
                executionId,
                idempotencyKey,
                AnalysisExecutionStatus.REQUESTED,
                null,
                policy,
                requestedAt,
                requestedAt,
                requestedAt.plus(policy.maximumDuration()),
                null,
                capabilities,
                RetryMetadata.none(policy.retryPolicy().maximumAttempts()),
                provenance,
                traceMetadata,
                null
        );
    }

    public static AnalysisExecution restore(
            UUID executionId, IdempotencyKey idempotencyKey,
            AnalysisExecutionStatus status, AnalysisResultQuality resultQuality,
            AnalysisExecutionPolicy executionPolicy, Instant requestedAt,
            Instant updatedAt, Instant expiresAt, Instant completedAt,
            List<String> capabilities, RetryMetadata retryMetadata,
            AnalysisExecutionProvenance provenance, AnalysisTraceMetadata traceMetadata,
            ConsolidatedIntelligence result) {
        return new AnalysisExecution(
                executionId, idempotencyKey, status, resultQuality, executionPolicy,
                requestedAt, updatedAt, expiresAt, completedAt, capabilities,
                retryMetadata, provenance, traceMetadata, result);
    }

    public AnalysisExecution transitionTo(AnalysisExecutionStatus target, Instant at) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(at);
        if (status.isTerminal() || !TRANSITIONS.getOrDefault(status, Set.of()).contains(target)) {
            throw new IllegalExecutionTransitionException(status, target);
        }
        return copy(
                target,
                resultQuality,
                at,
                target.isTerminal() ? at : null,
                retryMetadata,
                result
        );
    }

    public AnalysisExecution partiallyComplete(
            ConsolidatedIntelligence acceptedResult,
            AnalysisResultQuality quality,
            Instant at
    ) {
        if (quality == AnalysisResultQuality.COMPLETE) {
            throw new IllegalArgumentException("Partial completion cannot have COMPLETE quality");
        }
        AnalysisExecution transitioned =
                transitionTo(AnalysisExecutionStatus.PARTIALLY_COMPLETED, at);
        return transitioned.copy(
                transitioned.status,
                Objects.requireNonNull(quality),
                at,
                null,
                retryMetadata,
                Objects.requireNonNull(acceptedResult)
        );
    }

    public AnalysisExecution complete(
            ConsolidatedIntelligence acceptedResult,
            AnalysisResultQuality quality,
            Instant at
    ) {
        AnalysisExecution transitioned = transitionTo(AnalysisExecutionStatus.COMPLETED, at);
        return transitioned.copy(
                transitioned.status,
                Objects.requireNonNull(quality),
                at,
                at,
                retryMetadata,
                Objects.requireNonNull(acceptedResult)
        );
    }

    public boolean isExpiredAt(Instant now) {
        return !status.isTerminal() && !now.isBefore(expiresAt);
    }

    private AnalysisExecution copy(
            AnalysisExecutionStatus nextStatus,
            AnalysisResultQuality nextQuality,
            Instant nextUpdatedAt,
            Instant nextCompletedAt,
            RetryMetadata nextRetry,
            ConsolidatedIntelligence nextResult
    ) {
        return new AnalysisExecution(
                executionId, idempotencyKey, nextStatus, nextQuality, executionPolicy,
                requestedAt, nextUpdatedAt, expiresAt, nextCompletedAt, capabilities,
                nextRetry, provenance, traceMetadata, nextResult
        );
    }

    public UUID executionId() { return executionId; }
    public IdempotencyKey idempotencyKey() { return idempotencyKey; }
    public AnalysisExecutionStatus status() { return status; }
    public Optional<AnalysisResultQuality> resultQuality() {
        return Optional.ofNullable(resultQuality);
    }
    public AnalysisExecutionPolicy executionPolicy() { return executionPolicy; }
    public Instant requestedAt() { return requestedAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant expiresAt() { return expiresAt; }
    public Optional<Instant> completedAt() { return Optional.ofNullable(completedAt); }
    public List<String> capabilities() { return capabilities; }
    public RetryMetadata retryMetadata() { return retryMetadata; }
    public AnalysisExecutionProvenance provenance() { return provenance; }
    public AnalysisTraceMetadata traceMetadata() { return traceMetadata; }
    public Optional<ConsolidatedIntelligence> result() { return Optional.ofNullable(result); }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof AnalysisExecution other
                && executionId.equals(other.executionId)
                && status == other.status
                && updatedAt.equals(other.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionId, status, updatedAt);
    }
}
