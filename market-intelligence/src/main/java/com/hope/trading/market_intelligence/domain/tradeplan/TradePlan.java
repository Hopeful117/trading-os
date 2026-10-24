package com.hope.trading.market_intelligence.domain.tradeplan;

import java.time.Instant;
import java.util.*;

public final class TradePlan {
    private final TradePlanId id;
    private final TradePlanVersion version;
    private final TradePlanVersion previousVersion;
    private final TradePlanStatus status;
    private final TradePlanOrigin origin;
    private final UUID authorId;
    private final TradePlanningContextReference planningContext;
    private final ExecutionParameters execution;
    private final TradingRationale rationale;
    private final Instant createdAt;
    private final UUID decisionActorId;
    private final Instant decisionAt;

    TradePlan(
            TradePlanId id, TradePlanVersion version, TradePlanVersion previousVersion,
            TradePlanStatus status, TradePlanningContextReference planningContext,
            ExecutionParameters execution, TradingRationale rationale, Instant createdAt,
            TradePlanOrigin origin, UUID authorId, UUID decisionActorId, Instant decisionAt
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
        this.origin = Objects.requireNonNull(origin);
        if (origin == TradePlanOrigin.OPPORTUNITY && rationale.opportunities().isEmpty()) {
            throw new IllegalArgumentException("Opportunity is required for opportunity-origin plans");
        }
        if (origin == TradePlanOrigin.OPPORTUNITY && rationale.observations().isEmpty()) {
            throw new IllegalArgumentException("Observation is required for opportunity-origin plans");
        }
        if (origin == TradePlanOrigin.MANUAL && !rationale.opportunities().isEmpty()) {
            throw new IllegalArgumentException("Manual plans cannot reference Opportunities");
        }
        this.authorId = origin == TradePlanOrigin.MANUAL
                ? Objects.requireNonNull(authorId, "authorId") : authorId;
        this.planningContext = Objects.requireNonNull(planningContext);
        this.execution = Objects.requireNonNull(execution);
        this.rationale = Objects.requireNonNull(rationale);
        this.createdAt = Objects.requireNonNull(createdAt);
        if ((decisionActorId == null) != (decisionAt == null)) {
            throw new IllegalArgumentException("Decision actor and timestamp must be provided together");
        }
        this.decisionActorId = decisionActorId;
        this.decisionAt = decisionAt;
    }

    public TradePlanId id() { return id; }
    public TradePlanVersion version() { return version; }
    public Optional<TradePlanVersion> previousVersion() {
        return Optional.ofNullable(previousVersion);
    }
    public TradePlanStatus status() { return status; }
    public TradePlanOrigin origin() { return origin; }
    public Optional<UUID> authorId() { return Optional.ofNullable(authorId); }
    public TradePlanningContextReference planningContext() { return planningContext; }
    public ExecutionParameters execution() { return execution; }
    public TradingRationale rationale() { return rationale; }
    public Instant createdAt() { return createdAt; }
    public Optional<UUID> decisionActorId() { return Optional.ofNullable(decisionActorId); }
    public Optional<Instant> decisionAt() { return Optional.ofNullable(decisionAt); }
}
