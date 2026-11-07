package com.hope.trading.market_intelligence.domain.research;

import java.time.Instant;
import java.util.UUID;

public record HistoricalReconstructionLifecycle(
        UUID executionId,
        UUID leaseId,
        HistoricalReconstructionLifecycleStatus status,
        HistoricalReconstructionLifecyclePhase phase,
        String failureCode,
        String failureReason,
        String snapshotFingerprint,
        String outcomeFingerprint,
        String outputDirectory,
        Instant startedAt,
        Instant updatedAt,
        Instant completedAt
) {
    public HistoricalReconstructionLifecycle {
        if (executionId == null || leaseId == null || status == null || phase == null || startedAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Historical reconstruction lifecycle identity is required");
        }
        if (failureReason != null && failureReason.length() > 500) {
            throw new IllegalArgumentException("Historical reconstruction failure reason is too long");
        }
    }

    public static HistoricalReconstructionLifecycle started(UUID executionId, String outputDirectory, Instant now) {
        return started(executionId, UUID.randomUUID(), outputDirectory, now);
    }

    public static HistoricalReconstructionLifecycle started(
            UUID executionId, UUID leaseId, String outputDirectory, Instant now) {
        return new HistoricalReconstructionLifecycle(executionId,
                leaseId,
                HistoricalReconstructionLifecycleStatus.RUNNING,
                HistoricalReconstructionLifecyclePhase.STARTED, null, null, null, null,
                outputDirectory, now, now, null);
    }

    public HistoricalReconstructionLifecycle progress(
            HistoricalReconstructionLifecyclePhase nextPhase,
            String nextSnapshotFingerprint,
            String nextOutcomeFingerprint,
            Instant now
    ) {
        return new HistoricalReconstructionLifecycle(executionId, leaseId, status, nextPhase, failureCode, failureReason,
                nextSnapshotFingerprint == null ? snapshotFingerprint : nextSnapshotFingerprint,
                nextOutcomeFingerprint == null ? outcomeFingerprint : nextOutcomeFingerprint,
                outputDirectory, startedAt, now, completedAt);
    }

    public HistoricalReconstructionLifecycle claimed(UUID nextLeaseId, Instant now) {
        return claimed(nextLeaseId, outputDirectory, now);
    }

    public HistoricalReconstructionLifecycle claimed(UUID nextLeaseId, String nextOutputDirectory, Instant now) {
        return new HistoricalReconstructionLifecycle(executionId, nextLeaseId, status, phase, failureCode,
                failureReason, snapshotFingerprint, outcomeFingerprint, nextOutputDirectory, startedAt, now, completedAt);
    }

    public HistoricalReconstructionLifecycle success(String snapshot, String outcome, Instant now) {
        return new HistoricalReconstructionLifecycle(executionId,
                leaseId,
                HistoricalReconstructionLifecycleStatus.SUCCEEDED,
                HistoricalReconstructionLifecyclePhase.EXPORT_COMPLETED, null, null, snapshot, outcome,
                outputDirectory, startedAt, now, now);
    }

    public HistoricalReconstructionLifecycle failure(
            HistoricalReconstructionLifecycleStatus terminalStatus,
            String code,
            String reason,
            Instant now
    ) {
        if (terminalStatus != HistoricalReconstructionLifecycleStatus.FAILED
                && terminalStatus != HistoricalReconstructionLifecycleStatus.PARTIAL) {
            throw new IllegalArgumentException("Failure requires a terminal reconstruction status");
        }
        return new HistoricalReconstructionLifecycle(executionId, leaseId, terminalStatus, phase, code,
                reason == null ? null : reason.substring(0, Math.min(500, reason.length())),
                snapshotFingerprint, outcomeFingerprint, outputDirectory, startedAt, now, now);
    }
}
