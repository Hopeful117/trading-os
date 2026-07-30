package com.hope.trading.market_intelligence.domain.tradeplan;

import java.time.Instant;
import java.util.*;

public final class TradePlan {
    private final TradePlanId id;
    private final TradePlanVersion version;
    private final TradePlanVersion previousVersion;
    private final TradePlanStatus status;
    private final TradingContextReference tradingContext;
    private final ExecutionParameters execution;
    private final TradingRationale rationale;
    private final Instant createdAt;

    TradePlan(
            TradePlanId id, TradePlanVersion version, TradePlanVersion previousVersion,
            TradePlanStatus status, TradingContextReference tradingContext,
            ExecutionParameters execution, TradingRationale rationale, Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.version = Objects.requireNonNull(version);
        this.previousVersion = previousVersion;
        if (version.value() == 1 && previousVersion != null
                || version.value() > 1 && (previousVersion == null
                    || previousVersion.value() != version.value() - 1)) {
            throw new IllegalArgumentException("Invalid version lineage");
        }
        this.status = Objects.requireNonNull(status);
        this.tradingContext = Objects.requireNonNull(tradingContext);
        this.execution = Objects.requireNonNull(execution);
        this.rationale = Objects.requireNonNull(rationale);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public TradePlanId id() { return id; }
    public TradePlanVersion version() { return version; }
    public Optional<TradePlanVersion> previousVersion() {
        return Optional.ofNullable(previousVersion);
    }
    public TradePlanStatus status() { return status; }
    public TradingContextReference tradingContext() { return tradingContext; }
    public ExecutionParameters execution() { return execution; }
    public TradingRationale rationale() { return rationale; }
    public Instant createdAt() { return createdAt; }
}
