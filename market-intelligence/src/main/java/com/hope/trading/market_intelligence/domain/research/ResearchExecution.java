package com.hope.trading.market_intelligence.domain.research;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ResearchExecution {
    private final UUID researchId;
    private final UUID actorId;
    private final UUID researchScopeId;
    private final UUID ownerUserId;
    private final UUID initiatingAgentId;
    private final UUID credentialId;
    private final UUID grantId;
    private final Long grantVersion;
    private final Integer credentialVersion;
    private final String idempotencyKey;
    private final String requestFingerprint;
    private final List<UUID> marketIds;
    private final String objective;
    private final String depth;
    private final ResearchObjectiveType objectiveType;
    private final ResearchTemporalScope temporalScope;
    private final ResearchExecutionStatus status;
    private final Instant requestedAt;
    private final Instant updatedAt;
    private final Instant completedAt;
    private final String failureCode;
    private final JsonNode report;
    private final int attemptCount;
    private final ResearchExecutionStage currentStage;
    private final Instant lastProgressAt;

    private ResearchExecution(
            UUID researchId, UUID actorId, UUID researchScopeId, UUID ownerUserId, UUID initiatingAgentId,
            UUID credentialId, UUID grantId, Long grantVersion, Integer credentialVersion,
            String idempotencyKey, String requestFingerprint, List<UUID> marketIds, String objective,
            String depth, ResearchObjectiveType objectiveType, ResearchTemporalScope temporalScope,
            ResearchExecutionStatus status, Instant requestedAt, Instant updatedAt,
            Instant completedAt, String failureCode, JsonNode report, int attemptCount,
            ResearchExecutionStage currentStage, Instant lastProgressAt) {
        this.researchId = Objects.requireNonNull(researchId);
        this.actorId = Objects.requireNonNull(actorId);
        this.researchScopeId = researchScopeId;
        this.ownerUserId = ownerUserId;
        this.initiatingAgentId = initiatingAgentId;
        this.credentialId = credentialId;
        this.grantId = grantId;
        this.grantVersion = grantVersion;
        this.credentialVersion = credentialVersion;
        this.idempotencyKey = required(idempotencyKey, "idempotencyKey");
        this.requestFingerprint = required(requestFingerprint, "requestFingerprint");
        this.marketIds = List.copyOf(marketIds);
        this.objective = required(objective, "objective");
        this.depth = required(depth, "depth");
        this.objectiveType = Objects.requireNonNull(objectiveType, "objectiveType");
        this.temporalScope = temporalScope;
        this.status = Objects.requireNonNull(status);
        this.requestedAt = Objects.requireNonNull(requestedAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.completedAt = completedAt;
        this.failureCode = failureCode;
        this.report = report;
        this.attemptCount = attemptCount;
        this.currentStage = Objects.requireNonNull(currentStage);
        this.lastProgressAt = Objects.requireNonNull(lastProgressAt);
    }

    public static ResearchExecution requested(
            UUID researchId, UUID actorId, String idempotencyKey, String requestFingerprint,
            List<UUID> marketIds, String objective, String depth, Instant now) {
        return requested(researchId, actorId, null, idempotencyKey, requestFingerprint,
                marketIds, objective, depth, now);
    }

    public static ResearchExecution requested(
            UUID researchId, UUID actorId, UUID researchScopeId, String idempotencyKey,
            String requestFingerprint, List<UUID> marketIds, String objective, String depth, Instant now) {
        return new ResearchExecution(researchId, actorId, researchScopeId, null, null, null, null, null, null,
                idempotencyKey, requestFingerprint, marketIds, objective, depth,
                ResearchObjectiveType.COMPARATIVE_MARKET_RESEARCH, null,
                ResearchExecutionStatus.REQUESTED, now, now, null, null, null,
                0, ResearchExecutionStage.QUEUED, now);
    }

    public ResearchExecution running(Instant now) {
        return copy(ResearchExecutionStatus.RUNNING, now, null, null, report,
                attemptCount + 1, ResearchExecutionStage.DETERMINISTIC_ANALYSIS, now);
    }

    public ResearchExecution checkpoint(JsonNode result, Instant now) {
        return copy(ResearchExecutionStatus.RUNNING, now, null, null, result,
                attemptCount, ResearchExecutionStage.CHECKPOINTED, now);
    }

    public ResearchExecution progress(ResearchExecutionStage stage, Instant now) {
        if (status != ResearchExecutionStatus.RUNNING) {
            throw new IllegalStateException("Only running research can make progress");
        }
        return copy(status, now, completedAt, failureCode, report, attemptCount, stage, now);
    }

    public ResearchExecution requeue(Instant now) {
        return copy(ResearchExecutionStatus.REQUESTED, now, null, null, report,
                attemptCount, ResearchExecutionStage.REQUEUED, now);
    }

    public ResearchExecution complete(JsonNode result, Instant now) {
        return copy(ResearchExecutionStatus.COMPLETED, now, now, null, result,
                attemptCount, ResearchExecutionStage.COMPLETED, now);
    }

    public ResearchExecution partial(JsonNode result, Instant now) {
        return copy(ResearchExecutionStatus.PARTIALLY_COMPLETED, now, now, null, result,
                attemptCount, ResearchExecutionStage.PARTIALLY_COMPLETED, now);
    }

    public ResearchExecution fail(String code, Instant now) {
        return copy(ResearchExecutionStatus.FAILED, now, now, code, null,
                attemptCount, ResearchExecutionStage.FAILED, now);
    }

    public ResearchExecution expire(Instant now) {
        return copy(ResearchExecutionStatus.EXPIRED, now, now, "RESEARCH_EXECUTION_EXPIRED", null,
                attemptCount, ResearchExecutionStage.EXPIRED, now);
    }

    public static ResearchExecution restore(
            UUID researchId, UUID actorId, String idempotencyKey, String requestFingerprint,
            List<UUID> marketIds, String objective, String depth, ResearchExecutionStatus status,
            Instant requestedAt, Instant updatedAt, Instant completedAt, String failureCode,
            JsonNode report) {
        return restoreWithObservability(researchId, actorId, null, null, null, null, null, null, null,
                idempotencyKey, requestFingerprint, marketIds, objective, depth, status, requestedAt,
                updatedAt, completedAt, failureCode, report, 0, defaultStage(status), updatedAt);
    }

    public static ResearchExecution restore(
            UUID researchId, UUID actorId, UUID researchScopeId, String idempotencyKey,
            String requestFingerprint, List<UUID> marketIds, String objective, String depth,
            ResearchExecutionStatus status, Instant requestedAt, Instant updatedAt, Instant completedAt,
            String failureCode, JsonNode report) {
        return restoreWithObservability(researchId, actorId, researchScopeId, null, null, null, null, null, null,
                idempotencyKey, requestFingerprint, marketIds, objective, depth, status, requestedAt,
                updatedAt, completedAt, failureCode, report, 0, defaultStage(status), updatedAt);
    }

    public static ResearchExecution requestedWithProvenance(
            UUID researchId, UUID actorId, UUID researchScopeId, UUID ownerUserId, UUID initiatingAgentId,
            UUID credentialId, UUID grantId, Long grantVersion, Integer credentialVersion,
            String idempotencyKey, String requestFingerprint, List<UUID> marketIds,
            String objective, String depth, Instant now) {
        return new ResearchExecution(researchId, actorId, researchScopeId, ownerUserId, initiatingAgentId,
                credentialId, grantId, grantVersion, credentialVersion, idempotencyKey, requestFingerprint,
                marketIds, objective, depth, ResearchObjectiveType.COMPARATIVE_MARKET_RESEARCH, null,
                ResearchExecutionStatus.REQUESTED, now, now, null, null, null,
                0, ResearchExecutionStage.QUEUED, now);
    }

    public static ResearchExecution requestedWithProvenance(
            UUID researchId, UUID actorId, UUID researchScopeId, UUID ownerUserId, UUID initiatingAgentId,
            UUID credentialId, UUID grantId, Long grantVersion, Integer credentialVersion,
            String idempotencyKey, String requestFingerprint, List<UUID> marketIds,
            String objective, String depth, ResearchObjectiveType objectiveType,
            ResearchTemporalScope temporalScope, Instant now) {
        return new ResearchExecution(researchId, actorId, researchScopeId, ownerUserId, initiatingAgentId,
                credentialId, grantId, grantVersion, credentialVersion, idempotencyKey, requestFingerprint,
                marketIds, objective, depth, objectiveType, temporalScope,
                ResearchExecutionStatus.REQUESTED, now, now, null, null, null,
                0, ResearchExecutionStage.QUEUED, now);
    }

    public static ResearchExecution restoreWithProvenance(
            UUID researchId, UUID actorId, UUID researchScopeId, UUID ownerUserId, UUID initiatingAgentId,
            UUID credentialId, UUID grantId, Long grantVersion, Integer credentialVersion,
            String idempotencyKey, String requestFingerprint, List<UUID> marketIds, String objective,
            String depth, ResearchExecutionStatus status, Instant requestedAt, Instant updatedAt,
            Instant completedAt, String failureCode, JsonNode report) {
        return restoreWithObservability(researchId, actorId, researchScopeId, ownerUserId, initiatingAgentId,
                credentialId, grantId, grantVersion, credentialVersion, idempotencyKey, requestFingerprint,
                marketIds, objective, depth, status, requestedAt, updatedAt, completedAt, failureCode, report,
                0, defaultStage(status), updatedAt);
    }

    public static ResearchExecution restoreWithObservability(
            UUID researchId, UUID actorId, UUID researchScopeId, UUID ownerUserId, UUID initiatingAgentId,
            UUID credentialId, UUID grantId, Long grantVersion, Integer credentialVersion,
            String idempotencyKey, String requestFingerprint, List<UUID> marketIds, String objective,
            String depth, ResearchExecutionStatus status, Instant requestedAt, Instant updatedAt,
            Instant completedAt, String failureCode, JsonNode report, int attemptCount,
            ResearchExecutionStage currentStage, Instant lastProgressAt) {
        return new ResearchExecution(researchId, actorId, researchScopeId, ownerUserId, initiatingAgentId,
                credentialId, grantId, grantVersion, credentialVersion, idempotencyKey, requestFingerprint,
                marketIds, objective, depth, ResearchObjectiveType.COMPARATIVE_MARKET_RESEARCH, null,
                status, requestedAt, updatedAt, completedAt, failureCode, report,
                attemptCount, currentStage, lastProgressAt);
    }

    public static ResearchExecution restoreWithObservability(
            UUID researchId, UUID actorId, UUID researchScopeId, UUID ownerUserId, UUID initiatingAgentId,
            UUID credentialId, UUID grantId, Long grantVersion, Integer credentialVersion,
            String idempotencyKey, String requestFingerprint, List<UUID> marketIds, String objective,
            String depth, ResearchObjectiveType objectiveType, ResearchTemporalScope temporalScope,
            ResearchExecutionStatus status, Instant requestedAt, Instant updatedAt,
            Instant completedAt, String failureCode, JsonNode report, int attemptCount,
            ResearchExecutionStage currentStage, Instant lastProgressAt) {
        return new ResearchExecution(researchId, actorId, researchScopeId, ownerUserId, initiatingAgentId,
                credentialId, grantId, grantVersion, credentialVersion, idempotencyKey, requestFingerprint,
                marketIds, objective, depth, objectiveType, temporalScope, status, requestedAt, updatedAt,
                completedAt, failureCode, report, attemptCount, currentStage, lastProgressAt);
    }

    private ResearchExecution copy(ResearchExecutionStatus nextStatus, Instant nextUpdatedAt,
                                   Instant nextCompletedAt, String nextFailureCode, JsonNode nextReport,
                                   int nextAttemptCount, ResearchExecutionStage nextStage,
                                   Instant nextProgressAt) {
        return new ResearchExecution(researchId, actorId, researchScopeId, ownerUserId, initiatingAgentId,
                credentialId, grantId, grantVersion, credentialVersion, idempotencyKey, requestFingerprint,
                marketIds, objective, depth, objectiveType, temporalScope, nextStatus, requestedAt, nextUpdatedAt, nextCompletedAt,
                nextFailureCode, nextReport, nextAttemptCount, nextStage, nextProgressAt);
    }

    private static ResearchExecutionStage defaultStage(ResearchExecutionStatus status) {
        return switch (status) {
            case REQUESTED -> ResearchExecutionStage.QUEUED;
            case RUNNING -> ResearchExecutionStage.DETERMINISTIC_ANALYSIS;
            case PARTIALLY_COMPLETED -> ResearchExecutionStage.PARTIALLY_COMPLETED;
            case COMPLETED -> ResearchExecutionStage.COMPLETED;
            case FAILED -> ResearchExecutionStage.FAILED;
            case CANCELLED -> ResearchExecutionStage.CANCELLED;
            case EXPIRED -> ResearchExecutionStage.EXPIRED;
        };
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }

    public UUID researchId() { return researchId; }
    public UUID actorId() { return actorId; }
    public UUID researchScopeId() { return researchScopeId; }
    public UUID ownerUserId() { return ownerUserId; }
    public UUID initiatingAgentId() { return initiatingAgentId; }
    public UUID credentialId() { return credentialId; }
    public UUID grantId() { return grantId; }
    public Long grantVersion() { return grantVersion; }
    public Integer credentialVersion() { return credentialVersion; }
    public String idempotencyKey() { return idempotencyKey; }
    public String requestFingerprint() { return requestFingerprint; }
    public List<UUID> marketIds() { return marketIds; }
    public String objective() { return objective; }
    public String depth() { return depth; }
    public ResearchObjectiveType objectiveType() { return objectiveType; }
    public ResearchTemporalScope temporalScope() { return temporalScope; }
    public ResearchExecutionStatus status() { return status; }
    public Instant requestedAt() { return requestedAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant completedAt() { return completedAt; }
    public String failureCode() { return failureCode; }
    public JsonNode report() { return report; }
    public int attemptCount() { return attemptCount; }
    public ResearchExecutionStage currentStage() { return currentStage; }
    public Instant lastProgressAt() { return lastProgressAt; }
}
