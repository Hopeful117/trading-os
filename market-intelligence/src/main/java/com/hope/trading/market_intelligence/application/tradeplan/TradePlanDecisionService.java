package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.application.port.TradePlanRepository;
import com.hope.trading.market_intelligence.application.port.TradePlanningContextRepository;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Explicit trader decision on a PROPOSED plan (STORY-0023). Actor-bound,
 * latest-version-checked and lifecycle-policy-guarded; repeated identical
 * decisions are idempotent successes.
 */
@Service
public class TradePlanDecisionService {
    private final TradePlanRepository plans;
    private final TradePlanningContextRepository contexts;
    private final TradePlanApplicationService service;

    public TradePlanDecisionService(
            TradePlanRepository plans, TradePlanningContextRepository contexts,
            TradePlanApplicationService service) {
        this.plans = plans;
        this.contexts = contexts;
        this.service = service;
    }

    @Transactional
    public TradePlan decide(UUID planId, long version, UUID actorId, Decision decision) {
        TradePlan latest = plans.findLatest(new TradePlanId(planId))
                .orElseThrow(() -> notFound("TRADE_PLAN_NOT_FOUND"));
        if (latest.version().value() != version) {
            throw conflict("STALE_TRADE_PLAN_VERSION",
                    "Decisions apply only to the latest Trade Plan version");
        }
        TradePlanningContext context = contexts.find(
                        latest.planningContext().id(), latest.planningContext().version())
                .orElseThrow(() -> notFound("PLANNING_CONTEXT_NOT_FOUND"));
        if (!context.ownerId().equals(actorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TRADE_PLAN_FORBIDDEN");
        }
        TradePlanStatus target = decision == Decision.ACCEPT
                ? TradePlanStatus.ACCEPTED : TradePlanStatus.REJECTED;
        if (latest.status() == target) {
            return latest;
        }
        if (latest.status() != TradePlanStatus.PROPOSED) {
            throw conflict("TRADE_PLAN_NOT_PROPOSED",
                    "Only a PROPOSED Trade Plan can be " + target.name().toLowerCase());
        }
        return service.transition(latest.id(), target);
    }

    public TradePlan loadForActor(UUID planId, long version, UUID actorId) {
        TradePlan plan = plans.find(new TradePlanId(planId), new TradePlanVersion(version))
                .orElseThrow(() -> notFound("TRADE_PLAN_NOT_FOUND"));
        TradePlanningContext context = contexts.find(
                        plan.planningContext().id(), plan.planningContext().version())
                .orElseThrow(() -> notFound("PLANNING_CONTEXT_NOT_FOUND"));
        if (!context.ownerId().equals(actorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TRADE_PLAN_FORBIDDEN");
        }
        return plan;
    }

    private ResponseStatusException notFound(String code) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, code);
    }

    private ResponseStatusException conflict(String code, String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, code + ": " + reason);
    }

    public enum Decision { ACCEPT, REJECT }
}
