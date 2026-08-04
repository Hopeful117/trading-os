package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.domain.opportunity.*;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.math.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

final class TradePlanBuilder {
    private final TradePlanFactory factory;
    TradePlanBuilder(TradePlanFactory factory) { this.factory = factory; }

    TradePlan build(
            TradePlanId id, TradePlanVersion version, TradePlanVersion previous,
            PlanningInput input, TradePlanDraft draft, Instant createdAt) {
        if (!draft.conflicts().isEmpty()) throw new IllegalStateException("Conflicted draft");
        EntryStrategy entry = required(draft, ContributionType.ENTRY, EntryStrategy.class);
        StopLoss stop = required(draft, ContributionType.STOP_LOSS, StopLoss.class);
        @SuppressWarnings("unchecked")
        List<TakeProfit> targets = (List<TakeProfit>) requiredRaw(
                draft, ContributionType.TAKE_PROFIT);
        PositionSizing sizing = required(
                draft, ContributionType.POSITION_SIZING, PositionSizing.class);
        PlanExpiration expiration = required(
                draft, ContributionType.EXPIRATION, PlanExpiration.class);
        @SuppressWarnings("unchecked")
        Set<String> confirmation = (Set<String>) requiredRaw(
                draft, ContributionType.CONFIRMATION);
        @SuppressWarnings("unchecked")
        Set<String> invalidation = (Set<String>) requiredRaw(
                draft, ContributionType.INVALIDATION);
        @SuppressWarnings("unchecked")
        Set<String> management = (Set<String>) requiredRaw(
                draft, ContributionType.MANAGEMENT);
        String thesis = required(draft, ContributionType.THESIS, String.class);
        BigDecimal entryPrice = Objects.requireNonNull(entry.price(),
                "Planning requires an explicit reference price");
        BigDecimal risk = entryPrice.subtract(stop.price()).abs();
        BigDecimal reward = targets.getFirst().price().subtract(entryPrice).abs();
        ExecutionParameters execution = new ExecutionParameters(
                input.instrument(), input.direction(), entry, stop, targets, sizing,
                new RiskReward(reward.divide(risk, 4, RoundingMode.HALF_UP)),
                expiration, management);
        Set<OpportunityPlanReference> opportunities = input.opportunities().stream()
                .map(item -> new OpportunityPlanReference(item.id(), item.version()))
                .collect(Collectors.toUnmodifiableSet());
        Set<ObservationReference> observations = input.opportunities().stream()
                .flatMap(item -> item.observations().stream())
                .collect(Collectors.toUnmodifiableSet());
        Set<AiAnalysisReference> ai = input.opportunities().stream()
                .flatMap(item -> item.aiAnalyses().stream())
                .collect(Collectors.toUnmodifiableSet());
        TradingRationale rationale = new TradingRationale(
                opportunities, observations, ai, thesis, confirmation, invalidation);
        return factory.create(
                id, version, previous, TradePlanStatus.PROPOSED, input.context().reference(),
                execution, rationale, createdAt);
    }

    TradePlan transition(TradePlan previous, TradePlanStatus target, Instant createdAt) {
        return factory.create(
                previous.id(), previous.version().next(), previous.version(), target,
                previous.planningContext(), previous.execution(), previous.rationale(), createdAt);
    }
    private <T> T required(TradePlanDraft draft, ContributionType type, Class<T> expected) {
        Object value = requiredRaw(draft, type);
        if (!expected.isInstance(value)) {
            throw new IllegalArgumentException("Invalid " + type + " contribution");
        }
        return expected.cast(value);
    }
    private Object requiredRaw(TradePlanDraft draft, ContributionType type) {
        return draft.contribution(type).orElseThrow(
                () -> new IllegalArgumentException("Missing contribution: " + type)).value();
    }
}
