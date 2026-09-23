package com.hope.trading.market_intelligence.domain.trendcontext;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class TrendContextProfile {
    public static final String CONSERVATIVE_SWING_V1 = "CONSERVATIVE_SWING_V1";

    private final String profileId;
    private final String profileVersion;
    private final Map<TrendContextRole, TrendContextRoleDefinition> roles;
    private final boolean triggerRequired;
    private final int pivotRadius;
    private final int minimumSeparationBars;
    private final int minimumConfirmedSwings;
    private final int emaPeriod;
    private final int emaWarmupBars;
    private final int emaSlopeLookback;
    private final String emaSlopeThreshold;
    private final int atrPeriod;
    private final int atrBaselineLength;
    private final String abnormalAtrRatioThreshold;
    private final String extensionAtrMultiple;
    private final int pullbackMinimumBars;
    private final int reclaimWindowBars;
    private final int freshnessMultiplier;

    private TrendContextProfile(
            String profileId,
            String profileVersion,
            Map<TrendContextRole, TrendContextRoleDefinition> roles,
            boolean triggerRequired,
            int pivotRadius,
            int minimumSeparationBars,
            int minimumConfirmedSwings,
            int emaPeriod,
            int emaWarmupBars,
            int emaSlopeLookback,
            String emaSlopeThreshold,
            int atrPeriod,
            int atrBaselineLength,
            String abnormalAtrRatioThreshold,
            String extensionAtrMultiple,
            int pullbackMinimumBars,
            int reclaimWindowBars,
            int freshnessMultiplier
    ) {
        this.profileId = requireText(profileId, "profileId");
        this.profileVersion = requireText(profileVersion, "profileVersion");
        EnumMap<TrendContextRole, TrendContextRoleDefinition> copy =
                new EnumMap<>(TrendContextRole.class);
        copy.putAll(Objects.requireNonNull(roles, "roles is required"));
        if (!copy.containsKey(TrendContextRole.BIAS)
                || !copy.containsKey(TrendContextRole.SETUP)) {
            throw new IllegalArgumentException("BIAS and SETUP roles are required");
        }
        if (copy.size() > 3) {
            throw new IllegalArgumentException("Unsupported Trend Context role");
        }
        if (copy.containsKey(TrendContextRole.TRIGGER) && triggerRequired
                && !copy.get(TrendContextRole.TRIGGER).required()) {
            throw new IllegalArgumentException("Required TRIGGER must be required in its definition");
        }
        validateRoleOrdering(copy);
        this.roles = Map.copyOf(copy);
        this.triggerRequired = triggerRequired;
        this.pivotRadius = positive(pivotRadius, "pivotRadius");
        this.minimumSeparationBars = positive(minimumSeparationBars, "minimumSeparationBars");
        this.minimumConfirmedSwings = positive(minimumConfirmedSwings, "minimumConfirmedSwings");
        this.emaPeriod = positive(emaPeriod, "emaPeriod");
        this.emaWarmupBars = nonNegative(emaWarmupBars, "emaWarmupBars");
        this.emaSlopeLookback = positive(emaSlopeLookback, "emaSlopeLookback");
        this.emaSlopeThreshold = requireText(emaSlopeThreshold, "emaSlopeThreshold");
        this.atrPeriod = positive(atrPeriod, "atrPeriod");
        this.atrBaselineLength = positive(atrBaselineLength, "atrBaselineLength");
        this.abnormalAtrRatioThreshold = requireText(
                abnormalAtrRatioThreshold, "abnormalAtrRatioThreshold");
        this.extensionAtrMultiple = requireText(extensionAtrMultiple, "extensionAtrMultiple");
        this.pullbackMinimumBars = positive(pullbackMinimumBars, "pullbackMinimumBars");
        this.reclaimWindowBars = positive(reclaimWindowBars, "reclaimWindowBars");
        this.freshnessMultiplier = positive(freshnessMultiplier, "freshnessMultiplier");
    }

    public static TrendContextProfile conservativeSwingV1(
            Map<TrendContextRole, TrendContextRoleDefinition> roles
    ) {
        return conservativeSwingV1("1.0.0", roles);
    }

    public static TrendContextProfile conservativeSwingV1(
            String profileVersion,
            Map<TrendContextRole, TrendContextRoleDefinition> roles
    ) {
        return new TrendContextProfile(
                CONSERVATIVE_SWING_V1,
                profileVersion,
                roles,
                false,
                2,
                2,
                2,
                50,
                10,
                5,
                "0.10",
                14,
                20,
                "3.0",
                "3.0",
                2,
                2,
                2
        );
    }

    public String profileId() { return profileId; }
    public String profileVersion() { return profileVersion; }
    public Map<TrendContextRole, TrendContextRoleDefinition> roles() { return roles; }
    public boolean triggerRequired() { return triggerRequired; }
    public int pivotRadius() { return pivotRadius; }
    public int minimumSeparationBars() { return minimumSeparationBars; }
    public int minimumConfirmedSwings() { return minimumConfirmedSwings; }
    public int emaPeriod() { return emaPeriod; }
    public int emaWarmupBars() { return emaWarmupBars; }
    public int emaSlopeLookback() { return emaSlopeLookback; }
    public String emaSlopeThreshold() { return emaSlopeThreshold; }
    public int atrPeriod() { return atrPeriod; }
    public int atrBaselineLength() { return atrBaselineLength; }
    public String abnormalAtrRatioThreshold() { return abnormalAtrRatioThreshold; }
    public String extensionAtrMultiple() { return extensionAtrMultiple; }
    public int pullbackMinimumBars() { return pullbackMinimumBars; }
    public int reclaimWindowBars() { return reclaimWindowBars; }
    public int freshnessMultiplier() { return freshnessMultiplier; }

    private static void validateRoleOrdering(
            Map<TrendContextRole, TrendContextRoleDefinition> roles
    ) {
        TrendContextRoleDefinition bias = roles.get(TrendContextRole.BIAS);
        TrendContextRoleDefinition setup = roles.get(TrendContextRole.SETUP);
        if (!bias.intervalDuration().minus(setup.intervalDuration()).isPositive()) {
            throw new IllegalArgumentException("SETUP interval must be finer than BIAS");
        }
        TrendContextRoleDefinition trigger = roles.get(TrendContextRole.TRIGGER);
        if (trigger != null && !setup.intervalDuration().minus(trigger.intervalDuration()).isPositive()) {
            throw new IllegalArgumentException("TRIGGER interval must be finer than SETUP");
        }
    }

    private static int positive(int value, String field) {
        if (value < 1) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    private static int nonNegative(int value, String field) {
        if (value < 0) throw new IllegalArgumentException(field + " must not be negative");
        return value;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
