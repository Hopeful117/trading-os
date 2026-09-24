package com.hope.trading.market_intelligence.adapter.marketdata;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hope.trading.market_intelligence.domain.ContextPayload;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextRole;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable role-scoped source context assembled for one analysis boundary. */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public record TrendContextRoleHistory(
        Map<TrendContextRole, List<OhlcResponse>> responsesByRole,
        Instant assessmentAt,
        Instant cutOffAt,
        String profileId,
        String profileVersion,
        String ruleVersion,
        Map<TrendContextRole, Integer> requestedCandles
) implements ContextPayload {
    public TrendContextRoleHistory {
        EnumMap<TrendContextRole, List<OhlcResponse>> responses =
                new EnumMap<>(TrendContextRole.class);
        responsesByRole.forEach((role, values) -> responses.put(role, List.copyOf(values)));
        responsesByRole = Map.copyOf(responses);
        Objects.requireNonNull(assessmentAt, "assessmentAt is required");
        Objects.requireNonNull(cutOffAt, "cutOffAt is required");
        profileId = required(profileId, "profileId");
        profileVersion = required(profileVersion, "profileVersion");
        ruleVersion = required(ruleVersion, "ruleVersion");
        requestedCandles = Map.copyOf(requestedCandles);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
