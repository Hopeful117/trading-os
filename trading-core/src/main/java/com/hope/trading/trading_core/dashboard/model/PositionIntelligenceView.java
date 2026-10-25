package com.hope.trading.trading_core.dashboard.model;

import com.hope.trading.trading_core.risk.application.TradeScenarioProjectionResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PositionIntelligenceView(
        OpenPositionDashboardView position,
        String status,
        Lineage lineage,
        Rationale rationale,
        Comparisons comparisons,
        TradeScenarioProjectionResponse scenarios,
        SourceStatus accountData,
        SourceStatus marketData,
        SourceStatus challengeData,
        Instant generatedAt
) {
    public record SourceStatus(String status, Instant observedAt, String reason) { }
    public record Lineage(
            String status,
            UUID tradeOutcomeId,
            UUID executionIntentId,
            UUID tradePlanId,
            Long tradePlanVersion,
            String origin,
            Set<UUID> opportunityIds,
            String reason
    ) {
        public Lineage {
            opportunityIds = opportunityIds == null ? Set.of() : Set.copyOf(opportunityIds);
        }
    }

    public record Rationale(
            String status,
            String thesis,
            Set<String> confirmationConditions,
            Set<String> invalidationConditions,
            Set<String> managementRules,
            String reason
    ) {
        public Rationale {
            confirmationConditions = confirmationConditions == null ? Set.of() : Set.copyOf(confirmationConditions);
            invalidationConditions = invalidationConditions == null ? Set.of() : Set.copyOf(invalidationConditions);
            managementRules = managementRules == null ? Set.of() : Set.copyOf(managementRules);
        }
    }

    public record Comparisons(
            String status,
            BigDecimal priceChange,
            BigDecimal priceChangePercentage,
            BigDecimal distanceToStop,
            BigDecimal distanceToTarget,
            List<String> unavailableReasons
    ) {
        public Comparisons {
            unavailableReasons = unavailableReasons == null ? List.of() : List.copyOf(unavailableReasons);
        }
    }
}
