package com.hope.trading.market_intelligence.domain.scan;

import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityReason;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ActiveScanMarket {
    private final UUID scanMarketId;
    private final UUID scanId;
    private final int ordinal;
    private final UUID marketId;
    private final boolean eligible;
    private final List<MarketEligibilityReason> exclusionReasons;
    private final ActiveScanMarketStatus status;
    private final UUID analysisExecutionId;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ActiveScanMarket(
            UUID scanMarketId,
            UUID scanId,
            int ordinal,
            UUID marketId,
            boolean eligible,
            List<MarketEligibilityReason> exclusionReasons,
            ActiveScanMarketStatus status,
            UUID analysisExecutionId,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.scanMarketId = Objects.requireNonNull(scanMarketId);
        this.scanId = Objects.requireNonNull(scanId);
        if (ordinal < 0) throw new IllegalArgumentException("ordinal must be >= 0");
        this.ordinal = ordinal;
        this.marketId = Objects.requireNonNull(marketId);
        this.eligible = eligible;
        this.exclusionReasons = List.copyOf(exclusionReasons);
        this.status = Objects.requireNonNull(status);
        this.analysisExecutionId = analysisExecutionId;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        if (!eligible && status != ActiveScanMarketStatus.EXCLUDED) {
            throw new IllegalArgumentException("Excluded markets must use EXCLUDED status");
        }
        if (!eligible && analysisExecutionId != null) {
            throw new IllegalArgumentException("Excluded markets cannot link an analysis execution");
        }
        if (eligible && status == ActiveScanMarketStatus.EXCLUDED) {
            throw new IllegalArgumentException("Eligible markets cannot use EXCLUDED status");
        }
        if (eligible && analysisExecutionId == null) {
            throw new IllegalArgumentException("Eligible markets require analysis execution linkage");
        }
    }

    public static ActiveScanMarket excluded(
            UUID scanMarketId,
            UUID scanId,
            int ordinal,
            UUID marketId,
            List<MarketEligibilityReason> exclusionReasons,
            Instant createdAt
    ) {
        return new ActiveScanMarket(
                scanMarketId,
                scanId,
                ordinal,
                marketId,
                false,
                exclusionReasons,
                ActiveScanMarketStatus.EXCLUDED,
                null,
                createdAt,
                createdAt
        );
    }

    public static ActiveScanMarket registered(
            UUID scanMarketId,
            UUID scanId,
            int ordinal,
            UUID marketId,
            UUID analysisExecutionId,
            Instant createdAt
    ) {
        return new ActiveScanMarket(
                scanMarketId,
                scanId,
                ordinal,
                marketId,
                true,
                List.of(),
                ActiveScanMarketStatus.REGISTERED,
                analysisExecutionId,
                createdAt,
                createdAt
        );
    }

    public static ActiveScanMarket restore(
            UUID scanMarketId,
            UUID scanId,
            int ordinal,
            UUID marketId,
            boolean eligible,
            List<MarketEligibilityReason> exclusionReasons,
            ActiveScanMarketStatus status,
            UUID analysisExecutionId,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new ActiveScanMarket(
                scanMarketId,
                scanId,
                ordinal,
                marketId,
                eligible,
                exclusionReasons,
                status,
                analysisExecutionId,
                createdAt,
                updatedAt
        );
    }

    public ActiveScanMarket markDispatchRequested(Instant at) {
        Objects.requireNonNull(at);
        if (status != ActiveScanMarketStatus.REGISTERED) {
            throw new IllegalStateException("Only REGISTERED scan markets can request dispatch");
        }
        return new ActiveScanMarket(
                scanMarketId,
                scanId,
                ordinal,
                marketId,
                eligible,
                exclusionReasons,
                ActiveScanMarketStatus.DISPATCH_REQUESTED,
                analysisExecutionId,
                createdAt,
                at
        );
    }

    public UUID scanMarketId() { return scanMarketId; }
    public UUID scanId() { return scanId; }
    public int ordinal() { return ordinal; }
    public UUID marketId() { return marketId; }
    public boolean eligible() { return eligible; }
    public List<MarketEligibilityReason> exclusionReasons() { return exclusionReasons; }
    public ActiveScanMarketStatus status() { return status; }
    public UUID analysisExecutionId() { return analysisExecutionId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
