package com.hope.trading.market_intelligence.domain.passive;

import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class PassiveScanMarket {
    private final UUID scanMarketId;
    private final UUID runId;
    private final int ordinal;
    private final UUID marketId;
    private final String provider;
    private final String productType;
    private final Duration cadence;
    private final TradeabilityEvidenceProfile minimumEvidenceProfile;
    private final PassiveScanMarketStatus status;
    private final UUID analysisExecutionId;
    private final int attemptCount;
    private final String failureCode;
    private final String failureReason;
    private final Instant nextRetryAt;
    private final UUID leaseId;
    private final Instant leaseExpiresAt;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PassiveScanMarket(
            UUID scanMarketId, UUID runId, int ordinal, UUID marketId, String provider,
            String productType, Duration cadence, TradeabilityEvidenceProfile minimumEvidenceProfile,
            PassiveScanMarketStatus status, UUID analysisExecutionId, int attemptCount,
            String failureCode, String failureReason, Instant nextRetryAt, UUID leaseId,
            Instant leaseExpiresAt, long version, Instant createdAt, Instant updatedAt
    ) {
        this.scanMarketId = Objects.requireNonNull(scanMarketId);
        this.runId = Objects.requireNonNull(runId);
        if (ordinal < 0) throw new IllegalArgumentException("ordinal must be >= 0");
        this.ordinal = ordinal;
        this.marketId = Objects.requireNonNull(marketId);
        this.provider = require(provider, "provider");
        this.productType = require(productType, "productType");
        this.cadence = Objects.requireNonNull(cadence);
        if (cadence.isZero() || cadence.isNegative()) throw new IllegalArgumentException("cadence must be positive");
        this.minimumEvidenceProfile = Objects.requireNonNull(minimumEvidenceProfile);
        this.status = Objects.requireNonNull(status);
        this.analysisExecutionId = Objects.requireNonNull(analysisExecutionId);
        if (attemptCount < 1) throw new IllegalArgumentException("attemptCount must be positive");
        this.attemptCount = attemptCount;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.nextRetryAt = nextRetryAt;
        this.leaseId = leaseId;
        this.leaseExpiresAt = leaseExpiresAt;
        if (version < 0) throw new IllegalArgumentException("version must be non-negative");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static PassiveScanMarket registered(
            UUID scanMarketId, UUID runId, int ordinal, UUID marketId, String provider,
            String productType, Duration cadence, TradeabilityEvidenceProfile profile,
            UUID analysisExecutionId, Instant createdAt
    ) {
        return new PassiveScanMarket(scanMarketId, runId, ordinal, marketId, provider, productType,
                cadence, profile, PassiveScanMarketStatus.REGISTERED, analysisExecutionId, 1,
                null, null, null, null, null, 0, createdAt, createdAt);
    }

    public static PassiveScanMarket restore(
            UUID scanMarketId, UUID runId, int ordinal, UUID marketId, String provider,
            String productType, Duration cadence, TradeabilityEvidenceProfile profile,
            PassiveScanMarketStatus status, UUID analysisExecutionId, int attemptCount,
            String failureCode, String failureReason, Instant nextRetryAt, UUID leaseId,
            Instant leaseExpiresAt, long version, Instant createdAt, Instant updatedAt
    ) {
        return new PassiveScanMarket(scanMarketId, runId, ordinal, marketId, provider, productType,
                cadence, profile, status, analysisExecutionId, attemptCount, failureCode,
                failureReason, nextRetryAt, leaseId, leaseExpiresAt, version, createdAt, updatedAt);
    }

    public PassiveScanMarket advanceTo(PassiveScanMarketStatus target, Instant at) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(at);
        if (status == target) return this;
        if (status.isTerminal()) throw new IllegalStateException("Cannot transition terminal passive scan market");
        boolean valid = switch (status) {
            case REGISTERED -> target == PassiveScanMarketStatus.DISPATCH_REQUESTED
                    || target == PassiveScanMarketStatus.SKIPPED;
            case DISPATCH_REQUESTED -> target == PassiveScanMarketStatus.ACCEPTED
                    || target == PassiveScanMarketStatus.FAILED;
            case ACCEPTED -> target == PassiveScanMarketStatus.RUNNING
                    || target == PassiveScanMarketStatus.FAILED;
            case RUNNING -> target.isTerminal();
            default -> false;
        };
        if (!valid) throw new IllegalStateException("PassiveScanMarket cannot advance from " + status + " to " + target);
        return restore(scanMarketId, runId, ordinal, marketId, provider, productType, cadence,
                minimumEvidenceProfile, target, analysisExecutionId, attemptCount, failureCode,
                failureReason, nextRetryAt, leaseId, leaseExpiresAt, version + 1, createdAt, at);
    }

    public UUID scanMarketId() { return scanMarketId; }
    public UUID runId() { return runId; }
    public int ordinal() { return ordinal; }
    public UUID marketId() { return marketId; }
    public String provider() { return provider; }
    public String productType() { return productType; }
    public Duration cadence() { return cadence; }
    public TradeabilityEvidenceProfile minimumEvidenceProfile() { return minimumEvidenceProfile; }
    public PassiveScanMarketStatus status() { return status; }
    public UUID analysisExecutionId() { return analysisExecutionId; }
    public int attemptCount() { return attemptCount; }
    public String failureCode() { return failureCode; }
    public String failureReason() { return failureReason; }
    public Instant nextRetryAt() { return nextRetryAt; }
    public UUID leaseId() { return leaseId; }
    public Instant leaseExpiresAt() { return leaseExpiresAt; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
