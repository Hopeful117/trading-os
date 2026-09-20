package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public record ManualTradePlanningRequest(
        UUID planningContextId,
        long contextVersion,
        UUID actorId,
        String instrument,
        TradeDirection direction,
        EntryStrategy entry,
        StopLoss stopLoss,
        List<TakeProfit> takeProfits,
        PositionSizing positionSizing,
        BigDecimal referencePrice,
        Instant expiresAt,
        String expirationPolicy,
        String thesis,
        Set<String> confirmationConditions,
        Set<String> invalidationConditions,
        Set<String> managementRules
) {
    public ManualTradePlanningRequest {
        Objects.requireNonNull(planningContextId);
        if (contextVersion < 1) throw new IllegalArgumentException("Context version starts at 1");
        Objects.requireNonNull(actorId);
        instrument = Objects.requireNonNull(instrument).trim();
        if (instrument.isEmpty()) throw new IllegalArgumentException("instrument is required");
        Objects.requireNonNull(direction);
        Objects.requireNonNull(entry);
        Objects.requireNonNull(stopLoss);
        takeProfits = List.copyOf(takeProfits);
        if (takeProfits.isEmpty()) throw new IllegalArgumentException("A target is required");
        Objects.requireNonNull(positionSizing);
        if (Objects.requireNonNull(referencePrice).signum() <= 0) {
            throw new IllegalArgumentException("referencePrice must be positive");
        }
        Objects.requireNonNull(expiresAt);
        expirationPolicy = required(expirationPolicy, "expirationPolicy");
        thesis = required(thesis, "thesis");
        confirmationConditions = Set.copyOf(confirmationConditions);
        invalidationConditions = Set.copyOf(invalidationConditions);
        managementRules = Set.copyOf(managementRules);
        if (confirmationConditions.isEmpty() || invalidationConditions.isEmpty()) {
            throw new IllegalArgumentException("Confirmation and invalidation rules are required");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
