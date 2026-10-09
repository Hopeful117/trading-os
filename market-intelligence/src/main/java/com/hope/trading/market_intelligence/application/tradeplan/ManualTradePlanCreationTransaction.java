package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.application.port.ManualTradePlanIdempotencyRepository;
import com.hope.trading.market_intelligence.application.port.TradePlanEventPublisher;
import com.hope.trading.market_intelligence.application.port.TradePlanRepository;
import com.hope.trading.market_intelligence.application.port.TradePlanningMetrics;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanEvent;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanId;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManualTradePlanCreationTransaction {
    private final TradePlanningEngine engine;
    private final TradePlanRepository plans;
    private final ManualTradePlanIdempotencyRepository idempotency;
    private final TradePlanEventPublisher events;
    private final TradePlanningMetrics metrics;
    private final Clock clock;

    public ManualTradePlanCreationTransaction(
            TradePlanningEngine engine, TradePlanRepository plans,
            ManualTradePlanIdempotencyRepository idempotency,
            TradePlanEventPublisher events, TradePlanningMetrics metrics, Clock clock) {
        this.engine = engine;
        this.plans = plans;
        this.idempotency = idempotency;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public TradePlanningResult create(
            ManualTradePlanningRequest request, String key, String fingerprint, TradePlanId id) {
        if (idempotency.findByActorIdAndKey(request.actorId(), key).isPresent()) {
            throw new IllegalStateException("Manual Trade Plan idempotency key is already used");
        }
        var started = clock.instant();
        TradePlanningResult result = engine.planManual(request, id);
        metrics.recordDuration(Duration.between(started, clock.instant()));
        if (result instanceof TradePlanningResult.Success success) {
            plans.append(success.plan());
            idempotency.save(new ManualTradePlanIdempotencyRepository.Entry(
                    request.actorId(), key, fingerprint,
                    success.plan().id().value(), success.plan().version().value()));
            metrics.increment("trade_plans_created");
            events.publish(new TradePlanEvent.Created(
                    success.plan().id(), success.plan().version(), clock.instant()));
        }
        return result;
    }
}
