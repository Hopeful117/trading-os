package com.hope.trading.market_intelligence.domain.tradeplan;

import java.util.*;
import java.math.BigDecimal;

public record ExecutionParameters(
        String instrument, TradeDirection direction, EntryStrategy entry,
        StopLoss stopLoss, List<TakeProfit> takeProfits, PositionSizing positionSizing,
        RiskReward riskReward, PlanExpiration expiration, Set<String> managementRules,
        String instrumentProvider, String instrumentProductType, BigDecimal referencePrice
) {
    public ExecutionParameters(String instrument, TradeDirection direction, EntryStrategy entry,
                               StopLoss stopLoss, List<TakeProfit> takeProfits, PositionSizing positionSizing,
                               RiskReward riskReward, PlanExpiration expiration, Set<String> managementRules) {
        this(instrument, direction, entry, stopLoss, takeProfits, positionSizing, riskReward,
                expiration, managementRules, null, null);
    }
    public ExecutionParameters(String instrument, TradeDirection direction, EntryStrategy entry,
                               StopLoss stopLoss, List<TakeProfit> takeProfits, PositionSizing positionSizing,
                               RiskReward riskReward, PlanExpiration expiration, Set<String> managementRules,
                               String instrumentProvider, String instrumentProductType) {
        this(instrument, direction, entry, stopLoss, takeProfits, positionSizing, riskReward,
                expiration, managementRules, instrumentProvider, instrumentProductType, null);
    }
    public ExecutionParameters {
        instrument = Objects.requireNonNull(instrument).trim();
        if (instrument.isEmpty()) throw new IllegalArgumentException("instrument is required");
        Objects.requireNonNull(direction); Objects.requireNonNull(entry);
        Objects.requireNonNull(positionSizing); Objects.requireNonNull(expiration);
        takeProfits = List.copyOf(takeProfits == null ? List.of() : takeProfits);
        managementRules = Set.copyOf(managementRules);
        validateCoherence(direction, entry, stopLoss, takeProfits);
    }

    private static void validateCoherence(
            TradeDirection direction, EntryStrategy entry, StopLoss stopLoss,
            List<TakeProfit> takeProfits) {
        if (stopLoss == null) return;
        var entryPrice = entry.price();
        if (entryPrice == null) return;
        for (TakeProfit target : takeProfits) {
            boolean coherent = direction == TradeDirection.LONG
                    ? stopLoss.price().compareTo(entryPrice) < 0
                        && target.price().compareTo(entryPrice) > 0
                    : stopLoss.price().compareTo(entryPrice) > 0
                        && target.price().compareTo(entryPrice) < 0;
            if (!coherent) throw new IllegalArgumentException("Contradictory execution prices");
        }
    }
}
