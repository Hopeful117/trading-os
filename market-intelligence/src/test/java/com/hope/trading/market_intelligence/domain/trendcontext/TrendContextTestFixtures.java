package com.hope.trading.market_intelligence.domain.trendcontext;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class TrendContextTestFixtures {
    public static final UUID MARKET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final Instant ASSESSMENT_AT = Instant.parse("2026-09-24T12:00:00Z");
    public static final String RULE_VERSION = "trend-context-rules-v1";

    public static TrendContextAssessment assessment() {
        TrendContextProfile profile = profile();
        EnumMap<TrendContextRole, TrendContextRoleSeries> series =
                new EnumMap<>(TrendContextRole.class);
        series.put(TrendContextRole.BIAS, emptySeries(profile, TrendContextRole.BIAS));
        series.put(TrendContextRole.SETUP, emptySeries(profile, TrendContextRole.SETUP));
        return new TrendContextEngine().assess(TrendContextAssessmentInput.accept(
                MARKET_ID, "KRAKEN", "BTC/EUR", ASSESSMENT_AT, ASSESSMENT_AT,
                profile, RULE_VERSION, series));
    }

    public static TrendContextProfile profile() {
        EnumMap<TrendContextRole, TrendContextRoleDefinition> roles =
                new EnumMap<>(TrendContextRole.class);
        roles.put(TrendContextRole.BIAS, definition(TrendContextRole.BIAS, "FOUR_HOURS", true));
        roles.put(TrendContextRole.SETUP, definition(TrendContextRole.SETUP, "ONE_HOUR", true));
        roles.put(TrendContextRole.TRIGGER, definition(
                TrendContextRole.TRIGGER, "FIFTEEN_MINUTES", false));
        return TrendContextProfile.conservativeSwingV1(roles);
    }

    private static TrendContextRoleDefinition definition(
            TrendContextRole role, String interval, boolean required) {
        Duration duration = switch (interval) {
            case "FOUR_HOURS" -> Duration.ofHours(4);
            case "ONE_HOUR" -> Duration.ofHours(1);
            default -> Duration.ofMinutes(15);
        };
        return new TrendContextRoleDefinition(role, interval, duration, required, 1, 1);
    }

    private static TrendContextRoleSeries emptySeries(
            TrendContextProfile profile, TrendContextRole role) {
        TrendContextRoleDefinition definition = profile.roles().get(role);
        TrendContextSourceReference source = new TrendContextSourceReference(
                "market-data", "KRAKEN", MARKET_ID, "BTC/EUR", role,
                definition.interval(), null, null, null, ASSESSMENT_AT,
                "snapshot", "digest-" + role.name());
        TrendContextFreshness freshness = new TrendContextFreshness(
                definition.intervalDuration(), null, null, ASSESSMENT_AT,
                ASSESSMENT_AT, true, false);
        return TrendContextRoleSeries.of(
                role, definition.interval(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), source, freshness, ASSESSMENT_AT);
    }

    private TrendContextTestFixtures() {}
}
