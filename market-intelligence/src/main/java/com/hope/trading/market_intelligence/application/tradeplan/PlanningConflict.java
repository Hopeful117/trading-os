package com.hope.trading.market_intelligence.application.tradeplan;

public record PlanningConflict(
        ContributionType type, String existingSource, String conflictingSource,
        Object existingValue, Object conflictingValue
) {}
