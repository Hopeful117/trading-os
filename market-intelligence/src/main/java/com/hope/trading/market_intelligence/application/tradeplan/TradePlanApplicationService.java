package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.time.*;
import java.util.*;

public final class TradePlanApplicationService {
    private final TradePlanningEngine engine;
    private final TradePlanRepository repository;
    private final TradePlanLifecyclePolicy lifecycle;
    private final TradePlanEventPublisher events;
    private final TradePlanningMetrics metrics;
    private final Clock clock;

    public TradePlanApplicationService(
            TradePlanningEngine engine, TradePlanRepository repository,
            TradePlanLifecyclePolicy lifecycle, TradePlanEventPublisher events,
            TradePlanningMetrics metrics, Clock clock) {
        this.engine = engine; this.repository = repository; this.lifecycle = lifecycle;
        this.events = events; this.metrics = metrics; this.clock = clock;
    }

    public TradePlanningResult create(TradePlanningRequest request) {
        Instant started = clock.instant();
        TradePlanningResult result = engine.plan(request);
        metrics.recordDuration(Duration.between(started, clock.instant()));
        if (result instanceof TradePlanningResult.Success success) {
            repository.append(success.plan());
            boolean version = success.plan().version().value() > 1;
            metrics.increment(version ? "trade_plans_replanned" : "trade_plans_created");
            events.publish(version
                    ? new TradePlanEvent.VersionCreated(
                            success.plan().id(), success.plan().version(), clock.instant())
                    : new TradePlanEvent.Created(
                            success.plan().id(), success.plan().version(), clock.instant()));
            if (success.warnings().stream().anyMatch(
                    warning -> warning.startsWith("AI contribution rejected"))) {
                metrics.increment("trade_plan_ai_contribution_failures");
            }
        } else if (result instanceof TradePlanningResult.Failure failure
                && failure.reason() == PlanningFailureReason.POLICY_CONFLICT) {
            metrics.increment("trade_plan_policy_conflicts");
        }
        return result;
    }

    public TradePlan transition(TradePlanId id, TradePlanStatus target) {
        TradePlan current = repository.findLatest(id)
                .orElseThrow(() -> new NoSuchElementException("TradePlan not found"));
        lifecycle.validate(current.status(), target);
        TradePlan next = repository.append(engine.transition(current, target));
        switch (target) {
            case ACCEPTED -> {
                metrics.increment("trade_plans_accepted");
                events.publish(new TradePlanEvent.Accepted(id, next.version(), clock.instant()));
                events.publish(new TradePlanEvent.ReadyForRiskValidation(
                        id, next.version(), clock.instant()));
            }
            case REJECTED -> {
                metrics.increment("trade_plans_rejected");
                events.publish(new TradePlanEvent.Rejected(id, next.version(), clock.instant()));
            }
            case EXPIRED -> {
                metrics.increment("trade_plans_expired");
                events.publish(new TradePlanEvent.Expired(id, next.version(), clock.instant()));
            }
            default -> { }
        }
        return next;
    }
    public Optional<TradePlan> find(TradePlanId id, TradePlanVersion version) {
        return repository.find(id, version);
    }
    public Optional<TradePlan> latest(TradePlanId id) { return repository.findLatest(id); }
    public List<TradePlan> history(TradePlanId id) { return repository.history(id); }
}
