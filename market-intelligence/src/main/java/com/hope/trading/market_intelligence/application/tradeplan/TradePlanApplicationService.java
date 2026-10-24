package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public final class TradePlanApplicationService {
    private static final String TRADE_PLANS_CREATED = "trade_plans_created";

    private final TradePlanningEngine engine;
    private final TradePlanRepository repository;
    private final TradePlanLifecyclePolicy lifecycle;
    private final TradePlanEventPublisher events;
    private final TradePlanningMetrics metrics;
    private final Clock clock;
    private final ManualTradePlanIdempotencyRepository manualIdempotency;
    private final ManualTradePlanFingerprintFactory manualFingerprints;
    private final ManualTradePlanCreationTransaction manualCreation;

    public TradePlanApplicationService(
            TradePlanningEngine engine, TradePlanRepository repository,
            TradePlanLifecyclePolicy lifecycle, TradePlanEventPublisher events,
            TradePlanningMetrics metrics, Clock clock) {
        this(engine, repository, lifecycle, events, metrics, clock,
                null);
    }

    public TradePlanApplicationService(
            TradePlanningEngine engine, TradePlanRepository repository,
            TradePlanLifecyclePolicy lifecycle, TradePlanEventPublisher events,
            TradePlanningMetrics metrics, Clock clock,
            ManualTradePlanDependencies manualDependencies) {
        this.engine = engine; this.repository = repository; this.lifecycle = lifecycle;
        this.events = events; this.metrics = metrics; this.clock = clock;
        this.manualIdempotency = manualDependencies == null ? null : manualDependencies.idempotency();
        this.manualFingerprints = manualDependencies == null ? null : manualDependencies.fingerprints();
        this.manualCreation = manualDependencies == null ? null : manualDependencies.creation();
    }

    public TradePlanningResult create(TradePlanningRequest request) {
        Instant started = clock.instant();
        TradePlanningResult result = engine.plan(request);
        metrics.recordDuration(Duration.between(started, clock.instant()));
        if (result instanceof TradePlanningResult.Success(var plan, var warnings)) {
            repository.append(plan);
            boolean version = plan.version().value() > 1;
            metrics.increment(version ? "trade_plans_replanned" : TRADE_PLANS_CREATED);
            events.publish(version
                    ? new TradePlanEvent.VersionCreated(
                            plan.id(), plan.version(), clock.instant())
                    : new TradePlanEvent.Created(
                            plan.id(), plan.version(), clock.instant()));
            if (warnings.stream().anyMatch(
                    warning -> warning.startsWith("AI contribution rejected"))) {
                metrics.increment("trade_plan_ai_contribution_failures");
            }
        } else if (result instanceof TradePlanningResult.Failure failure
                && failure.reason() == PlanningFailureReason.POLICY_CONFLICT) {
            metrics.increment("trade_plan_policy_conflicts");
        }
        return result;
    }

    public TradePlanningResult createManual(ManualTradePlanningRequest request) {
        Instant started = clock.instant();
        TradePlanningResult result = engine.planManual(request);
        metrics.recordDuration(Duration.between(started, clock.instant()));
        if (result instanceof TradePlanningResult.Success success) {
            repository.append(success.plan());
            metrics.increment(TRADE_PLANS_CREATED);
            events.publish(new TradePlanEvent.Created(
                    success.plan().id(), success.plan().version(), clock.instant()));
        }
        return result;
    }

    public TradePlanningResult createManual(
            ManualTradePlanningRequest request, String idempotencyKey) {
        if (manualFingerprints == null) {
            return createManual(request);
        }
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty()) {
            throw new TradePlanIdempotencyException(
                    "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required", 400);
        }
        if (key.length() > 200) {
            throw new TradePlanIdempotencyException(
                    "IDEMPOTENCY_KEY_INVALID", "Idempotency-Key must not exceed 200 characters", 400);
        }
        String fingerprint = manualFingerprints.fingerprint(request);
        var existing = manualIdempotency.findByActorIdAndKey(request.actorId(), key).orElse(null);
        if (existing != null) return replayOrConflict(existing, fingerprint);

        TradePlanId deterministicId = new TradePlanId(UUID.nameUUIDFromBytes(
                (request.actorId() + ":" + request.tradingAccountId() + ":" + key)
                        .getBytes(StandardCharsets.UTF_8)));
        TradePlanningResult result;
        try {
            result = manualCreation == null
                    ? createManual(request, deterministicId)
                    : manualCreation.create(request, key, fingerprint, deterministicId);
        } catch (IllegalStateException race) {
            var winner = manualIdempotency.findByActorIdAndKey(request.actorId(), key)
                    .orElseThrow(() -> race);
            return replayOrConflict(winner, fingerprint);
        }
        if (result instanceof TradePlanningResult.Failure
                && repository.find(deterministicId, new TradePlanVersion(1)).isPresent()) {
            var winner = manualIdempotency.findByActorIdAndKey(request.actorId(), key)
                    .orElseThrow(() -> new TradePlanIdempotencyException(
                            "IDEMPOTENCY_IN_PROGRESS",
                            "A manual Trade Plan with this Idempotency-Key is still being created", 409));
            return replayOrConflict(winner, fingerprint);
        }
        return result;
    }

    private TradePlanningResult createManual(
            ManualTradePlanningRequest request, TradePlanId identifier) {
        Instant started = clock.instant();
        TradePlanningResult result = engine.planManual(request, identifier);
        metrics.recordDuration(Duration.between(started, clock.instant()));
        if (result instanceof TradePlanningResult.Success success) {
            repository.append(success.plan());
            metrics.increment(TRADE_PLANS_CREATED);
            events.publish(new TradePlanEvent.Created(
                    success.plan().id(), success.plan().version(), clock.instant()));
        }
        return result;
    }

    private TradePlanningResult replayOrConflict(
            ManualTradePlanIdempotencyRepository.Entry entry, String fingerprint) {
        if (!entry.fingerprint().equals(fingerprint)) {
            throw new TradePlanIdempotencyException(
                    "IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key is already bound to another manual Trade Plan request", 409);
        }
        TradePlan plan = repository.find(new TradePlanId(entry.tradePlanId()),
                        new TradePlanVersion(entry.tradePlanVersion()))
                .orElseThrow(() -> new TradePlanIdempotencyException(
                        "IDEMPOTENCY_STATE_INVALID",
                        "Manual Trade Plan idempotency record points to a missing plan", 500));
        return new TradePlanningResult.Success(plan, List.of());
    }

    public TradePlan transition(TradePlanId id, TradePlanStatus target) {
        return transition(id, target, null);
    }

    public TradePlan transition(TradePlanId id, TradePlanStatus target, UUID decisionActorId) {
        if ((target == TradePlanStatus.ACCEPTED || target == TradePlanStatus.REJECTED)
                && decisionActorId == null) {
            throw new IllegalArgumentException("A decision actor is required for TradePlan decisions");
        }
        TradePlan current = repository.findLatest(id)
                .orElseThrow(() -> new NoSuchElementException("TradePlan not found"));
        if (decisionActorId != null
                && (target == TradePlanStatus.ACCEPTED || target == TradePlanStatus.REJECTED)
                && current.status() == target) {
            return current;
        }
        lifecycle.validate(current.status(), target);
        TradePlan next = repository.append(engine.transition(current, target, decisionActorId));
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
            default -> { /* No event is emitted for intermediate states. */ }
        }
        return next;
    }
    public Optional<TradePlan> find(TradePlanId id, TradePlanVersion version) {
        return repository.find(id, version);
    }
    public Optional<TradePlan> latest(TradePlanId id) { return repository.findLatest(id); }
    public List<TradePlan> history(TradePlanId id) { return repository.history(id); }

    public record ManualTradePlanDependencies(
            ManualTradePlanIdempotencyRepository idempotency,
            ManualTradePlanFingerprintFactory fingerprints,
            ManualTradePlanCreationTransaction creation
    ) { }
}
