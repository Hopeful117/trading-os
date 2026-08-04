package com.hope.trading.market_intelligence.application.tradeplan;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record TradePlanRiskSnapshot(
        UUID tradePlanId, long tradePlanVersion, String status, Instant createdAt,
        Context context, Execution execution, Rationale rationale
) {
    public record Context(
            UUID id, long version, Instant capturedAt, UUID ownerId, UUID tradingAccountId,
            String accountCurrency, UUID riskBudgetSourceId, long riskBudgetSourceVersion,
            UUID planningPreferencesId, long planningPreferencesVersion
    ) { }

    public record Execution(
            String instrument, String direction, Entry entry, StopLoss stopLoss,
            List<TakeProfit> takeProfits, PositionSizing positionSizing,
            BigDecimal riskRewardRatio, Expiration expiration, Set<String> managementRules
    ) { }

    public record Entry(String type, BigDecimal price, Set<String> conditions) { }

    public record StopLoss(BigDecimal price, String rationale) { }

    public record TakeProfit(BigDecimal price, BigDecimal allocationPercent) { }

    public record PositionSizing(
            BigDecimal quantity, BigDecimal notional, BigDecimal expectedMonetaryRisk,
            String currency
    ) { }

    public record Expiration(Instant expiresAt, String policy) { }

    public record Rationale(
            Set<Opportunity> opportunities, Set<UUID> observationIds,
            Set<UUID> aiAnalysisIds, String thesis, Set<String> confirmationConditions,
            Set<String> invalidationConditions
    ) { }

    public record Opportunity(UUID id, long version) { }
}
