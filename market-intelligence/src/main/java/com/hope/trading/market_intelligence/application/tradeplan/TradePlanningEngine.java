package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.time.*;
import java.util.*;

/** Exclusive public creation boundary for immutable TradePlan versions. */
public final class TradePlanningEngine {
    private final TradingOpportunityRepository opportunities;
    private final TradePlanningContextRepository contexts;
    private final TradePlanningContextAccessPolicy access;
    private final TradePlanRepository plans;
    private final PlanningPolicyRegistry policies;
    private final AiTradePlanningPort ai;
    private final AiContributionValidator aiValidator;
    private final TradePlanBuilder builder;
    private final TradePlanIdentifierGenerator identifiers;
    private final Clock clock;

    public TradePlanningEngine(
            TradingOpportunityRepository opportunities, TradePlanningContextRepository contexts,
            TradePlanningContextAccessPolicy access, TradePlanRepository plans,
            PlanningPolicyRegistry policies, AiTradePlanningPort ai,
            AiContributionValidator aiValidator, TradePlanFactory factory,
            TradePlanIdentifierGenerator identifiers, Clock clock) {
        this.opportunities = opportunities; this.contexts = contexts; this.access = access;
        this.plans = plans; this.policies = policies; this.ai = ai;
        this.aiValidator = aiValidator; this.builder = new TradePlanBuilder(factory);
        this.identifiers = identifiers; this.clock = clock;
    }

    public TradePlanningResult plan(TradePlanningRequest request) {
        try {
            List<TradingOpportunity> loaded = request.opportunityIds().stream()
                    .map(id -> opportunities.findLatest(id).orElseThrow(
                            () -> new IllegalArgumentException("Unknown Opportunity: " + id.value())))
                    .toList();
            if (!compatible(loaded)) {
                return failure(PlanningFailureReason.INCOMPATIBLE_OPPORTUNITIES,
                        "Opportunities must be active and share instrument and direction");
            }
            TradePlanningContext context = contexts.find(
                            request.planningContextId(), request.contextVersion())
                    .orElse(null);
            if (context == null || !access.mayUse(request.actorId(), context)) {
                return failure(PlanningFailureReason.INVALID_TRADING_CONTEXT,
                        "Trading Context is missing or unauthorized");
            }
            PlanningInput input = new PlanningInput(
                    loaded, context, request.marketPrice(), clock.instant());
            TradePlanDraft draft = new TradePlanDraft();
            List<PlanningContribution> deterministic = new ArrayList<>();
            for (PlanningPolicy policy : policies.applicable(input)) {
                PlanningContribution contribution = policy.evaluate(input);
                deterministic.add(contribution);
                draft.apply(contribution);
            }
            if (!draft.conflicts().isEmpty()) {
                return new TradePlanningResult.Failure(
                        PlanningFailureReason.POLICY_CONFLICT,
                        "Material planning contributions conflict", draft.conflicts());
            }
            try {
                for (PlanningContribution contribution :
                        aiValidator.validate(ai.propose(input, deterministic), input)) {
                    draft.apply(contribution);
                }
            } catch (RuntimeException rejectedAi) {
                draft.warning("AI contribution rejected: " + rejectedAi.getMessage());
            }
            if (!draft.conflicts().isEmpty()) {
                return new TradePlanningResult.Failure(
                        PlanningFailureReason.POLICY_CONFLICT,
                        "AI and deterministic contributions conflict", draft.conflicts());
            }
            TradePlan predecessor = predecessor(request);
            TradePlan plan = builder.build(
                    predecessor == null ? identifiers.next() : predecessor.id(),
                    predecessor == null ? new TradePlanVersion(1)
                            : predecessor.version().next(),
                    predecessor == null ? null : predecessor.version(),
                    input, draft, clock.instant());
            return new TradePlanningResult.Success(plan, draft.warnings());
        } catch (IllegalArgumentException missing) {
            return failure(PlanningFailureReason.INSUFFICIENT_DATA, missing.getMessage());
        } catch (RuntimeException invariant) {
            return failure(PlanningFailureReason.DOMAIN_INVARIANT, invariant.getMessage());
        }
    }

    TradePlan transition(TradePlan previous, TradePlanStatus target) {
        return builder.transition(previous, target, clock.instant());
    }
    private TradePlan predecessor(TradePlanningRequest request) {
        if (request.predecessorId() == null) return null;
        return plans.find(request.predecessorId(), request.predecessorVersion())
                .orElseThrow(() -> new IllegalArgumentException("Predecessor not found"));
    }
    private boolean compatible(List<TradingOpportunity> values) {
        if (values.isEmpty()) return false;
        TradingOpportunity first = values.getFirst();
        return values.stream().allMatch(item ->
                item.status() == OpportunityStatus.ACTIVE
                && item.instrument().equalsIgnoreCase(first.instrument())
                && item.direction() == first.direction());
    }
    private TradePlanningResult.Failure failure(
            PlanningFailureReason reason, String explanation) {
        return new TradePlanningResult.Failure(reason, explanation, List.of());
    }
}
