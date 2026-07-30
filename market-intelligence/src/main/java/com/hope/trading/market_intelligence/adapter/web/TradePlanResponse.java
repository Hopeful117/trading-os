package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public record TradePlanResponse(
        UUID id, long version, Long previousVersion, String status,
        UUID tradingContextId, long tradingContextVersion, Instant contextSnapshotAt,
        String instrument, String direction, String entryType, BigDecimal entryPrice,
        BigDecimal stopLoss, List<BigDecimal> takeProfits, BigDecimal quantity,
        BigDecimal notional, BigDecimal monetaryRisk, BigDecimal riskReward,
        Instant expiresAt, String thesis, Set<UUID> opportunityIds,
        Set<UUID> observationIds, Set<UUID> aiAnalysisIds,
        Set<String> confirmationConditions, Set<String> invalidationConditions,
        Set<String> managementRules, Instant createdAt
) {
    static TradePlanResponse from(TradePlan plan) {
        ExecutionParameters execution = plan.execution();
        TradingRationale rationale = plan.rationale();
        return new TradePlanResponse(
                plan.id().value(), plan.version().value(),
                plan.previousVersion().map(TradePlanVersion::value).orElse(null),
                plan.status().name(), plan.tradingContext().id(),
                plan.tradingContext().version(), plan.tradingContext().snapshotAt(),
                execution.instrument(), execution.direction().name(),
                execution.entry().type().name(), execution.entry().price(),
                execution.stopLoss().price(),
                execution.takeProfits().stream().map(TakeProfit::price).toList(),
                execution.positionSizing().quantity(), execution.positionSizing().notional(),
                execution.positionSizing().expectedMonetaryRisk(),
                execution.riskReward().ratio(), execution.expiration().expiresAt(),
                rationale.thesis(),
                rationale.opportunities().stream().map(item -> item.id().value())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                rationale.observations().stream().map(item -> item.observationId())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                rationale.aiAnalyses().stream().map(item -> item.analysisId())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                rationale.confirmationConditions(), rationale.invalidationConditions(),
                execution.managementRules(), plan.createdAt());
    }
}
