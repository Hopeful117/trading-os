package com.hope.trading.market_intelligence.domain.scan;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ActiveScan {
    private final UUID scanId;
    private final UUID actorId;
    private final UUID accountId;
    private final String objective;
    private final String idempotencyKey;
    private final String requestFingerprint;
    private final ActiveScanScopeSnapshot scopeSnapshot;
    private final ActiveScanStatus status;
    private final Instant resolvedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ActiveScan(
            UUID scanId,
            UUID actorId,
            UUID accountId,
            String objective,
            String idempotencyKey,
            String requestFingerprint,
            ActiveScanScopeSnapshot scopeSnapshot,
            ActiveScanStatus status,
            Instant resolvedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.scanId = Objects.requireNonNull(scanId);
        this.actorId = Objects.requireNonNull(actorId);
        this.accountId = Objects.requireNonNull(accountId);
        this.objective = Objects.requireNonNull(objective);
        this.idempotencyKey = require(idempotencyKey, "Idempotency-Key");
        this.requestFingerprint = require(requestFingerprint, "requestFingerprint");
        this.scopeSnapshot = Objects.requireNonNull(scopeSnapshot);
        this.status = Objects.requireNonNull(status);
        this.resolvedAt = Objects.requireNonNull(resolvedAt);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static ActiveScan readyToDispatch(
            UUID scanId,
            UUID actorId,
            UUID accountId,
            String objective,
            String idempotencyKey,
            String requestFingerprint,
            ActiveScanScopeSnapshot scopeSnapshot,
            Instant createdAt
    ) {
        return new ActiveScan(
                scanId,
                actorId,
                accountId,
                normalizeObjective(objective),
                idempotencyKey,
                requestFingerprint,
                scopeSnapshot,
                ActiveScanStatus.READY_TO_DISPATCH,
                scopeSnapshot.resolvedAt(),
                createdAt,
                createdAt
        );
    }

    public static ActiveScan completedNoWork(
            UUID scanId,
            UUID actorId,
            UUID accountId,
            String objective,
            String idempotencyKey,
            String requestFingerprint,
            ActiveScanScopeSnapshot scopeSnapshot,
            Instant createdAt
    ) {
        return new ActiveScan(
                scanId,
                actorId,
                accountId,
                normalizeObjective(objective),
                idempotencyKey,
                requestFingerprint,
                scopeSnapshot,
                ActiveScanStatus.COMPLETED_NO_WORK,
                scopeSnapshot.resolvedAt(),
                createdAt,
                createdAt
        );
    }

    public static ActiveScan restore(
            UUID scanId,
            UUID actorId,
            UUID accountId,
            String objective,
            String idempotencyKey,
            String requestFingerprint,
            ActiveScanScopeSnapshot scopeSnapshot,
            ActiveScanStatus status,
            Instant resolvedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new ActiveScan(
                scanId,
                actorId,
                accountId,
                objective,
                idempotencyKey,
                requestFingerprint,
                scopeSnapshot,
                status,
                resolvedAt,
                createdAt,
                updatedAt
        );
    }

    public ActiveScan markDispatchRequested(Instant at) {
        Objects.requireNonNull(at);
        if (status == ActiveScanStatus.DISPATCH_REQUESTED) {
            return this;
        }
        if (status != ActiveScanStatus.READY_TO_DISPATCH) {
            throw new IllegalStateException("Only READY_TO_DISPATCH scans can request dispatch");
        }
        return new ActiveScan(
                scanId,
                actorId,
                accountId,
                objective,
                idempotencyKey,
                requestFingerprint,
                scopeSnapshot,
                ActiveScanStatus.DISPATCH_REQUESTED,
                resolvedAt,
                createdAt,
                at
        );
    }

    public ActiveScan reconcileTo(ActiveScanStatus target, Instant at) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(at);
        if (status == target || status.isTerminal()) {
            return this;
        }
        if (!status.canAdvanceTo(target)) {
            throw new IllegalStateException(
                    "ActiveScan status cannot advance from %s to %s".formatted(status, target)
            );
        }
        return new ActiveScan(
                scanId,
                actorId,
                accountId,
                objective,
                idempotencyKey,
                requestFingerprint,
                scopeSnapshot,
                target,
                resolvedAt,
                createdAt,
                at
        );
    }

    public UUID scanId() { return scanId; }
    public UUID actorId() { return actorId; }
    public UUID accountId() { return accountId; }
    public String objective() { return objective; }
    public String idempotencyKey() { return idempotencyKey; }
    public String requestFingerprint() { return requestFingerprint; }
    public ActiveScanScopeSnapshot scopeSnapshot() { return scopeSnapshot; }
    public ActiveScanStatus status() { return status; }
    public Instant resolvedAt() { return resolvedAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    private static String normalizeObjective(String value) {
        return value == null ? "" : value.strip();
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }
}
