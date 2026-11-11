package com.hope.trading.market_data.model;

import java.time.Instant;

public final class HistoricalOhlcBackfillApproval {
    private final String reference;
    private final String planFingerprint;
    private final Instant approvedAt;
    private final Instant expiresAt;
    private boolean consumed;

    public HistoricalOhlcBackfillApproval(String reference, String planFingerprint,
                                          Instant approvedAt, Instant expiresAt) {
        if (reference == null || reference.isBlank() || planFingerprint == null || planFingerprint.isBlank()
                || approvedAt == null || expiresAt == null || !approvedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("Backfill approval reference and time are required");
        }
        this.reference = reference;
        this.planFingerprint = planFingerprint;
        this.approvedAt = approvedAt;
        this.expiresAt = expiresAt;
    }

    public synchronized void consume(String expectedPlanFingerprint, Instant now) {
        if (!planFingerprint.equals(expectedPlanFingerprint)) {
            throw new IllegalArgumentException("Backfill approval does not match the plan");
        }
        if (consumed) {
            throw new IllegalStateException("Backfill approval has already been consumed");
        }
        if (now == null || now.isBefore(approvedAt) || !now.isBefore(expiresAt)) {
            throw new IllegalStateException("Backfill approval is expired or not yet valid");
        }
        consumed = true;
    }

    public String reference() { return reference; }
    public String planFingerprint() { return planFingerprint; }
    public Instant approvedAt() { return approvedAt; }
    public Instant expiresAt() { return expiresAt; }
    public synchronized boolean consumed() { return consumed; }
}
