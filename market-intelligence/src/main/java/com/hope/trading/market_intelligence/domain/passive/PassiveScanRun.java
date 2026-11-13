package com.hope.trading.market_intelligence.domain.passive;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PassiveScanRun {
    private final UUID runId;
    private final String idempotencyKey;
    private final String requestFingerprint;
    private final String triggerContext;
    private final Instant plannedWindowStart;
    private final Instant plannedWindowEnd;
    private final String configurationVersion;
    private final String fingerprintAlgorithm;
    private final String universeFingerprint;
    private final Instant resolvedAt;
    private final PassiveScanRunStatus status;
    private final List<String> diagnostics;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PassiveScanRun(
            UUID runId, String idempotencyKey, String requestFingerprint,
            String triggerContext, Instant plannedWindowStart, Instant plannedWindowEnd,
            String configurationVersion, String fingerprintAlgorithm, String universeFingerprint,
            Instant resolvedAt, PassiveScanRunStatus status, List<String> diagnostics,
            Instant createdAt, Instant updatedAt
    ) {
        this.runId = Objects.requireNonNull(runId);
        this.idempotencyKey = require(idempotencyKey, "idempotencyKey");
        this.requestFingerprint = require(requestFingerprint, "requestFingerprint");
        this.triggerContext = require(triggerContext, "triggerContext");
        this.plannedWindowStart = Objects.requireNonNull(plannedWindowStart);
        this.plannedWindowEnd = Objects.requireNonNull(plannedWindowEnd);
        if (!plannedWindowStart.isBefore(plannedWindowEnd)) {
            throw new IllegalArgumentException("planned window start must be before end");
        }
        this.configurationVersion = require(configurationVersion, "configurationVersion");
        this.fingerprintAlgorithm = require(fingerprintAlgorithm, "fingerprintAlgorithm");
        this.universeFingerprint = require(universeFingerprint, "universeFingerprint");
        this.resolvedAt = Objects.requireNonNull(resolvedAt);
        this.status = Objects.requireNonNull(status);
        this.diagnostics = List.copyOf(diagnostics);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static PassiveScanRun requested(
            UUID runId, String idempotencyKey, String requestFingerprint,
            String triggerContext, Instant plannedWindowStart, Instant plannedWindowEnd,
            String configurationVersion, String fingerprintAlgorithm, String universeFingerprint,
            Instant resolvedAt, List<String> diagnostics, Instant createdAt
    ) {
        return new PassiveScanRun(runId, idempotencyKey, requestFingerprint, triggerContext,
                plannedWindowStart, plannedWindowEnd, configurationVersion, fingerprintAlgorithm,
                universeFingerprint, resolvedAt, PassiveScanRunStatus.REQUESTED, diagnostics,
                createdAt, createdAt);
    }

    public static PassiveScanRun completedNoWork(
            UUID runId, String idempotencyKey, String requestFingerprint,
            String triggerContext, Instant plannedWindowStart, Instant plannedWindowEnd,
            String configurationVersion, String fingerprintAlgorithm, String universeFingerprint,
            Instant resolvedAt, List<String> diagnostics, Instant createdAt
    ) {
        return new PassiveScanRun(runId, idempotencyKey, requestFingerprint, triggerContext,
                plannedWindowStart, plannedWindowEnd, configurationVersion, fingerprintAlgorithm,
                universeFingerprint, resolvedAt, PassiveScanRunStatus.COMPLETED_NO_WORK, diagnostics,
                createdAt, createdAt);
    }

    public static PassiveScanRun restore(
            UUID runId, String idempotencyKey, String requestFingerprint,
            String triggerContext, Instant plannedWindowStart, Instant plannedWindowEnd,
            String configurationVersion, String fingerprintAlgorithm, String universeFingerprint,
            Instant resolvedAt, PassiveScanRunStatus status, List<String> diagnostics,
            Instant createdAt, Instant updatedAt
    ) {
        return new PassiveScanRun(runId, idempotencyKey, requestFingerprint, triggerContext,
                plannedWindowStart, plannedWindowEnd, configurationVersion, fingerprintAlgorithm,
                universeFingerprint, resolvedAt, status, diagnostics, createdAt, updatedAt);
    }

    public PassiveScanRun advanceTo(PassiveScanRunStatus target, Instant at) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(at);
        if (status == target || status.isTerminal()) return this;
        boolean valid = switch (status) {
            case REQUESTED -> target == PassiveScanRunStatus.DISPATCH_REQUESTED
                    || target == PassiveScanRunStatus.RUNNING
                    || target == PassiveScanRunStatus.FAILED;
            case DISPATCH_REQUESTED -> target == PassiveScanRunStatus.RUNNING
                    || target == PassiveScanRunStatus.FAILED;
            case RUNNING -> target.isTerminal();
            default -> false;
        };
        if (!valid) throw new IllegalStateException("PassiveScanRun cannot advance from " + status + " to " + target);
        return restore(runId, idempotencyKey, requestFingerprint, triggerContext,
                plannedWindowStart, plannedWindowEnd, configurationVersion, fingerprintAlgorithm,
                universeFingerprint, resolvedAt, target, diagnostics, createdAt, at);
    }

    public UUID runId() { return runId; }
    public String idempotencyKey() { return idempotencyKey; }
    public String requestFingerprint() { return requestFingerprint; }
    public String triggerContext() { return triggerContext; }
    public Instant plannedWindowStart() { return plannedWindowStart; }
    public Instant plannedWindowEnd() { return plannedWindowEnd; }
    public String configurationVersion() { return configurationVersion; }
    public String fingerprintAlgorithm() { return fingerprintAlgorithm; }
    public String universeFingerprint() { return universeFingerprint; }
    public Instant resolvedAt() { return resolvedAt; }
    public PassiveScanRunStatus status() { return status; }
    public List<String> diagnostics() { return diagnostics; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
