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
    private final ResearchExecutionStatus status;
    private final Instant requestedAt;
    private final Instant updatedAt;
    private final Instant completedAt;
    private final String failureCode;
    private final JsonNode report;

    private ResearchExecution(
            UUID researchId, UUID actorId, UUID researchScopeId, UUID ownerUserId, UUID initiatingAgentId,
            UUID credentialId, UUID grantId, Long grantVersion, Integer credentialVersion,
            String idempotencyKey, String requestFingerprint,
            List<UUID> marketIds, String objective, String depth, ResearchExecutionStatus status,
            Instant requestedAt, Instant updatedAt, Instant completedAt, String failureCode,
            JsonNode report) {
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
        this.status = Objects.requireNonNull(status);
        this.requestedAt = Objects.requireNonNull(requestedAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.completedAt = completedAt;
        this.failureCode = failureCode;
        this.report = report;
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
                idempotencyKey, requestFingerprint,
                marketIds, objective, depth, ResearchExecutionStatus.REQUESTED, now, now,
                null, null, null);
    }

    public ResearchExecution running(Instant now) {
        return copy(ResearchExecutionStatus.RUNNING, now, null, null, report);
    }

    public ResearchExecution checkpoint(JsonNode result, Instant now) {
        return copy(ResearchExecutionStatus.RUNNING, now, null, null, result);
    }

    public ResearchExecution requeue(Instant now) {
        return copy(ResearchExecutionStatus.REQUESTED, now, null, null, report);
    }

    public ResearchExecution complete(JsonNode result, Instant now) {
        return copy(ResearchExecutionStatus.COMPLETED, now, now, null, result);
    }

    public ResearchExecution partial(JsonNode result, Instant now) {
        return copy(ResearchExecutionStatus.PARTIALLY_COMPLETED, now, now, null, result);
    }

    public ResearchExecution fail(String code, Instant now) {
        return copy(ResearchExecutionStatus.FAILED, now, now, code, null);
    }

    public ResearchExecution expire(Instant now) {
        return copy(ResearchExecutionStatus.EXPIRED, now, now, "RESEARCH_EXECUTION_EXPIRED", null);
    }

    public static ResearchExecution restore(
            UUID researchId, UUID actorId, String idempotencyKey, String requestFingerprint,
            List<UUID> marketIds, String objective, String depth, ResearchExecutionStatus status,
            Instant requestedAt, Instant updatedAt, Instant completedAt, String failureCode,
            JsonNode report) {
        return new ResearchExecution(researchId, actorId, null, null, null, null, null, null, null,
                idempotencyKey, requestFingerprint,
                marketIds, objective, depth, status, requestedAt, updatedAt, completedAt,
                failureCode, report);
    }

    public static ResearchExecution restore(
            UUID researchId, UUID actorId, UUID researchScopeId, String idempotencyKey, String requestFingerprint,
            List<UUID> marketIds, String objective, String depth, ResearchExecutionStatus status,
            Instant requestedAt, Instant updatedAt, Instant completedAt, String failureCode, JsonNode report) {
        return new ResearchExecution(researchId, actorId, researchScopeId, null, null, null, null, null, null,
                idempotencyKey, requestFingerprint,
                marketIds, objective, depth, status, requestedAt, updatedAt, completedAt, failureCode, report);
    }

    public static ResearchExecution requestedWithProvenance(
            UUID researchId, UUID actorId, UUID researchScopeId, UUID ownerUserId, UUID initiatingAgentId,
            UUID credentialId, UUID grantId, Long grantVersion, Integer credentialVersion,
            String idempotencyKey, String requestFingerprint, List<UUID> marketIds,
            String objective, String depth, Instant now) {
        return new ResearchExecution(researchId, actorId, researchScopeId, ownerUserId, initiatingAgentId,
                credentialId, grantId, grantVersion, credentialVersion, idempotencyKey, requestFingerprint,
                marketIds, objective, depth, ResearchExecutionStatus.REQUESTED, now, now, null, null, null);
    }

    public static ResearchExecution restoreWithProvenance(
            UUID researchId, UUID actorId, UUID researchScopeId, UUID ownerUserId, UUID initiatingAgentId,
            UUID credentialId, UUID grantId, Long grantVersion, Integer credentialVersion,
            String idempotencyKey, String requestFingerprint, List<UUID> marketIds, String objective,
            String depth, ResearchExecutionStatus status, Instant requestedAt, Instant updatedAt,
            Instant completedAt, String failureCode, JsonNode report) {
        return new ResearchExecution(researchId, actorId, researchScopeId, ownerUserId, initiatingAgentId,
                credentialId, grantId, grantVersion, credentialVersion, idempotencyKey, requestFingerprint,
                marketIds, objective, depth, status, requestedAt, updatedAt, completedAt, failureCode, report);
    }

    private ResearchExecution copy(ResearchExecutionStatus nextStatus, Instant nextUpdatedAt,
                                   Instant nextCompletedAt, String nextFailureCode, JsonNode nextReport) {
        return new ResearchExecution(researchId, actorId, researchScopeId, ownerUserId, initiatingAgentId,
                credentialId, grantId, grantVersion, credentialVersion, idempotencyKey, requestFingerprint,
                marketIds, objective, depth, nextStatus, requestedAt, nextUpdatedAt,
                nextCompletedAt, nextFailureCode, nextReport);
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
    public ResearchExecutionStatus status() { return status; }
    public Instant requestedAt() { return requestedAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant completedAt() { return completedAt; }
    public String failureCode() { return failureCode; }
    public JsonNode report() { return report; }
}
