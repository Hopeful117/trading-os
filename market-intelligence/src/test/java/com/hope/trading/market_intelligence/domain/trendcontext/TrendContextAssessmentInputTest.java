package com.hope.trading.market_intelligence.domain.trendcontext;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrendContextAssessmentInputTest {
    private static final UUID MARKET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant ASSESSMENT_AT = Instant.parse("2026-09-23T12:00:00Z");
    private static final Instant CUTOFF_AT = Instant.parse("2026-09-23T11:00:00Z");
    private static final Instant FETCHED_AT = Instant.parse("2026-09-23T11:05:00Z");

    @Test
    void acceptsBiasAndSetupWithOptionalTriggerAbsent() {
        TrendContextAssessmentInput input = input(profile(), realCandle("bias", "4H"),
                realCandle("setup", "1H"));

        assertThat(input.roleSeries()).containsKeys(TrendContextRole.BIAS, TrendContextRole.SETUP)
                .doesNotContainKey(TrendContextRole.TRIGGER);
    }

    @Test
    void acceptsConfiguredTrigger() {
        TrendContextProfile profile = profileWithTrigger();
        TrendContextAssessmentInput input = inputWithTrigger(profile,
                realCandle("bias", "4H"), realCandle("setup", "1H"),
                realCandle("trigger", "15M"));

        assertThat(input.roleSeries()).containsKey(TrendContextRole.TRIGGER);
    }

    @Test
    void rejectsMissingRequiredBiasAndSetup() {
        TrendContextProfile profile = profile();
        TrendContextCandle setup = realCandle("setup", "1H");
        TrendContextCandle bias = realCandle("bias", "4H");
        assertThatThrownBy(() -> input(profile, (TrendContextCandle) null, setup))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REQUIRED_ROLE_MISSING");
        assertThatThrownBy(() -> input(profile, bias, (TrendContextCandle) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REQUIRED_ROLE_MISSING");
    }

    @Test
    void rejectsRoleIntervalMismatchAndInvalidOrdering() {
        TrendContextProfile profile = profile();
        TrendContextCandle bias = realCandle("bias", "4H");
        TrendContextCandle setup = realCandle("setup", "15M");
        assertThatThrownBy(() -> input(profile, bias, setup))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_INTERVAL_MISMATCH");

        EnumMap<TrendContextRole, TrendContextRoleDefinition> invalidOrder = roles("1H", "4H");
        assertThatThrownBy(() -> TrendContextProfile.conservativeSwingV1(invalidOrder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SETUP interval must be finer");

        EnumMap<TrendContextRole, TrendContextRoleDefinition> equalOrder = roles("1H", "1H");
        assertThatThrownBy(() -> TrendContextProfile.conservativeSwingV1(equalOrder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SETUP interval must be finer");
    }

    @Test
    void rejectsProfilesWithOptionalRequiredRolesOrUndefinedRequiredTrigger() {
        EnumMap<TrendContextRole, TrendContextRoleDefinition> optionalBias = roles("4H", "1H");
        optionalBias.put(TrendContextRole.BIAS, new TrendContextRoleDefinition(
                TrendContextRole.BIAS, "4H", Duration.ofHours(4), false, 1, 1));
        assertThatThrownBy(() -> TrendContextProfile.conservativeSwingV1(optionalBias))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BIAS and SETUP roles are required");

        assertThatThrownBy(() -> TrendContextProfile.conservativeSwingV1(
                "1.0.0", roles("4H", "1H"), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Required TRIGGER must be defined");
    }

    @Test
    void rejectsRoleDefinitionWhoseDeclaredRoleDiffersFromProfileKey() {
        EnumMap<TrendContextRole, TrendContextRoleDefinition> invalid = roles("4H", "1H");
        invalid.put(TrendContextRole.BIAS, new TrendContextRoleDefinition(
                TrendContextRole.SETUP, "1H", Duration.ofHours(1), true, 1, 1));

        assertThatThrownBy(() -> TrendContextProfile.conservativeSwingV1(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match profile key");
    }

    @Test
    void rejectsInvalidRoleParameters() {
        assertThatThrownBy(() -> new TrendContextRoleDefinition(
                TrendContextRole.BIAS, "4H", Duration.ZERO, true, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        Duration invalidMinimumDuration = Duration.ofHours(4);
        assertThatThrownBy(() -> new TrendContextRoleDefinition(
                TrendContextRole.BIAS, "4H", invalidMinimumDuration, true, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
        EnumMap<TrendContextRole, TrendContextRoleDefinition> validOrder = roles("4H", "1H");
        assertThatThrownBy(() -> TrendContextProfile.conservativeSwingV1("", validOrder))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedOhlcAndImpossibleTimestamps() {
        TrendContextCandle malformed = candle("1H", "100", "99", "95", "98",
                true, false, "bad-ohlc", CUTOFF_AT.minus(Duration.ofHours(1)),
                CUTOFF_AT, CUTOFF_AT);
        TrendContextProfile profile = profile();
        TrendContextCandle bias = realCandle("bias", "4H");
        assertThatThrownBy(() -> input(profile, bias, malformed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID_OHLC");

        TrendContextCandle impossible = candle("1H", "100", "105", "95", "102",
                true, false, "bad-time", CUTOFF_AT, CUTOFF_AT.minusSeconds(1), CUTOFF_AT);
        assertThatThrownBy(() -> input(profile, bias, impossible))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID_TIMESTAMP");
    }

    @Test
    void excludesOpenSyntheticAndCutoffCrossingEvidenceWithoutRejectingInput() {
        TrendContextCandle eligible = realCandle("eligible", "1H");
        TrendContextCandle open = candle("1H", "100", "105", "95", "102",
                false, false, "open", CUTOFF_AT.minus(Duration.ofHours(2)),
                CUTOFF_AT.minus(Duration.ofHours(1)), FETCHED_AT);
        TrendContextCandle synthetic = candle("1H", "100", "100", "100", "100",
                true, true, "synthetic", CUTOFF_AT.minus(Duration.ofHours(3)),
                CUTOFF_AT.minus(Duration.ofHours(2)), FETCHED_AT);
        TrendContextCandle crossing = candle("1H", "100", "105", "95", "102",
                true, false, "crossing", CUTOFF_AT, CUTOFF_AT.plus(Duration.ofHours(1)), FETCHED_AT);

        TrendContextAssessmentInput input = input(profile(), realCandle("bias", "4H"),
                List.of(eligible, open, synthetic, crossing));
        TrendContextRoleSeries series = input.roleSeries().get(TrendContextRole.SETUP);

        assertThat(series.totalNormalizedCandleCount()).isEqualTo(4);
        assertThat(series.calculationReadyCandleCount()).isEqualTo(1);
        assertThat(series.excludedCandleCount()).isEqualTo(3);
        assertThat(series.exclusionFindings()).hasSize(3);
        assertThat(input.validationFindings()).extracting(TrendContextInputValidation.Finding::code)
                .contains("OPEN_CANDLE_EXCLUDED", "SYNTHETIC_DATA_EXCLUDED", "CUTOFF_EXCLUDED");
    }

    @Test
    void rejectsFutureCandleAfterAssessmentTime() {
        TrendContextCandle future = candle("1H", "100", "105", "95", "102", true, false,
                "future", ASSESSMENT_AT.plusSeconds(1), ASSESSMENT_AT.plus(Duration.ofHours(1)), FETCHED_AT);

        assertThatThrownBy(() -> input(profile(), realCandle("bias", "4H"), future))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FUTURE_CANDLE");
    }

    @Test
    void rejectsRoleSeriesWhoseIdentityDiffersFromMapKey() {
        EnumMap<TrendContextRole, TrendContextRoleSeries> roles = new EnumMap<>(TrendContextRole.class);
        roles.put(TrendContextRole.BIAS, series(TrendContextRole.SETUP,
                List.of(realCandle("bias", "4H")), CUTOFF_AT));
        roles.put(TrendContextRole.SETUP, series(TrendContextRole.SETUP,
                List.of(realCandle("setup", "1H")), CUTOFF_AT));

        assertThatThrownBy(() -> TrendContextAssessmentInput.accept(new TrendContextAssessmentInput.Values(
                MARKET_ID, "KRAKEN", "BTC/EUR", ASSESSMENT_AT, CUTOFF_AT,
                profile(), "rules-1", roles)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_IDENTITY_MISMATCH");
    }

    @Test
    void rejectsCandlesWhoseIntervalDiffersFromTheRoleSeries() {
        EnumMap<TrendContextRole, TrendContextRoleSeries> roles = new EnumMap<>(TrendContextRole.class);
        roles.put(TrendContextRole.BIAS, series(TrendContextRole.BIAS,
                List.of(realCandle("bias", "4H")), CUTOFF_AT));
        roles.put(TrendContextRole.SETUP, seriesWithMetadata(TrendContextRole.SETUP,
                List.of(realCandle("setup", "15M")), CUTOFF_AT,
                "1H", MARKET_ID, "KRAKEN", "BTC/EUR", true, true));

        assertThatThrownBy(() -> TrendContextAssessmentInput.accept(new TrendContextAssessmentInput.Values(
                MARKET_ID, "KRAKEN", "BTC/EUR", ASSESSMENT_AT, CUTOFF_AT,
                profile(), "rules-1", roles)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CANDLE_IDENTITY_MISMATCH");
    }

    @Test
    void rejectsRoleSourceIdentityThatDiffersFromTopLevelInput() {
        EnumMap<TrendContextRole, TrendContextRoleSeries> roles = new EnumMap<>(TrendContextRole.class);
        roles.put(TrendContextRole.BIAS, seriesWithMetadata(TrendContextRole.BIAS,
                List.of(realCandle("bias", "4H")), CUTOFF_AT,
                "4H", UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "KRAKEN", "BTC/EUR", true, true));
        roles.put(TrendContextRole.SETUP, series(TrendContextRole.SETUP,
                List.of(realCandle("setup", "1H")), CUTOFF_AT));

        assertThatThrownBy(() -> TrendContextAssessmentInput.accept(new TrendContextAssessmentInput.Values(
                MARKET_ID, "KRAKEN", "BTC/EUR", ASSESSMENT_AT, CUTOFF_AT,
                profile(), "rules-1", roles)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SOURCE_IDENTITY_MISMATCH");
    }

    @Test
    void rejectsDuplicateCandlesWithDifferentProvenance() {
        TrendContextCandle first = realCandle("duplicate-a", "1H");
        TrendContextCandle second = candle("1H", "100", "105", "95", "102", true, false,
                "duplicate-b", first.openTime(), first.closeTime(), FETCHED_AT);

        assertThatThrownBy(() -> input(profile(), realCandle("bias", "4H"), List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DUPLICATE_CONFLICT");
    }

    @Test
    void recordsInsufficientEligibleHistoryAsNonBlockingFinding() {
        TrendContextProfile profile = profile(2, "1.0.0");
        TrendContextAssessmentInput input = input(profile, realCandle("bias", "4H"),
                realCandle("setup", "1H"));

        assertThat(input.validationFindings()).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo("INSUFFICIENT_HISTORY");
            assertThat(finding.role()).isEqualTo(TrendContextRole.SETUP);
        });
    }

    @Test
    void fingerprintIsStableAcrossMapAndCandleOrdering() {
        TrendContextCandle setupB = candle("1H", "100", "105", "95", "102", true, false,
                "setup-b", CUTOFF_AT.minus(Duration.ofHours(2)),
                CUTOFF_AT.minus(Duration.ofHours(1)), FETCHED_AT);
        TrendContextAssessmentInput first = input(profile(), realCandle("bias", "4H"),
                List.of(realCandle("setup-a", "1H"), setupB));
        TrendContextAssessmentInput second = inputWithRoleOrder(profile(),
                List.of(candle("1H", "100", "105", "95", "102", true, false,
                                "setup-b", CUTOFF_AT.minus(Duration.ofHours(2)),
                                CUTOFF_AT.minus(Duration.ofHours(1)), FETCHED_AT),
                        realCandle("setup-a", "1H")), true,
                realCandle("bias", "4H"));

        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
    }

    @Test
    void fingerprintChangesForMaterialEvidenceAndContractValues() {
        String base = input(profile(), realCandle("bias", "4H"), realCandle("setup", "1H"))
                .fingerprint();
        assertThat(input(profile(), realCandle("bias", "4H"),
                candle("1H", "101", "106", "96", "103", true, false,
                        "setup", CUTOFF_AT.minus(Duration.ofHours(1)), CUTOFF_AT, FETCHED_AT)).fingerprint())
                .isNotEqualTo(base);
        assertThat(input(profile(), realCandle("bias", "4H"),
                candle("1H", "100", "105", "95", "102", false, false,
                        "setup", CUTOFF_AT.minus(Duration.ofHours(1)), CUTOFF_AT, FETCHED_AT)).fingerprint())
                .isNotEqualTo(base);
        assertThat(input(profile(), realCandle("bias", "4H"),
                candle("1H", "100", "105", "95", "102", true, true,
                        "setup-synthetic", CUTOFF_AT.minus(Duration.ofHours(1)), CUTOFF_AT, FETCHED_AT)).fingerprint())
                .isNotEqualTo(base);
        assertThat(input(profile(), realCandle("bias", "4H"),
                candle("1H", "100", "105", "95", "102", true, false,
                        "setup-other", CUTOFF_AT.minus(Duration.ofHours(1)), CUTOFF_AT, FETCHED_AT)).fingerprint())
                .isNotEqualTo(base);
        assertThat(input(profile(), realCandle("bias", "4H"),
                candle("1H", "100", "105", "95", "102", true, false,
                        "setup", CUTOFF_AT.minus(Duration.ofHours(1)), CUTOFF_AT, FETCHED_AT.plusSeconds(1))).fingerprint())
                .isNotEqualTo(base);
        assertThat(input(profile(), realCandle("bias", "4H"), realCandle("setup", "1H"),
                CUTOFF_AT.minusSeconds(1), "rules-1", profile(1, "1.0.1")).fingerprint())
                .isNotEqualTo(base);
        assertThat(input(profile(), realCandle("bias", "4H"), realCandle("setup", "1H"),
                CUTOFF_AT, "rules-2", profile(1, "1.0.0")).fingerprint()).isNotEqualTo(base);

        EnumMap<TrendContextRole, TrendContextRoleDefinition> triggerRoles = roles("4H", "1H");
        triggerRoles.put(TrendContextRole.TRIGGER, new TrendContextRoleDefinition(
                TrendContextRole.TRIGGER, "15M", Duration.ofMinutes(15), false, 1, 2));
        TrendContextProfile optionalTriggerProfile = TrendContextProfile.conservativeSwingV1(triggerRoles);
        assertThat(input(optionalTriggerProfile, realCandle("bias", "4H"), realCandle("setup", "1H"))
                .fingerprint()).isNotEqualTo(base);

        EnumMap<TrendContextRole, TrendContextRoleSeries> freshnessRoles = new EnumMap<>(TrendContextRole.class);
        freshnessRoles.put(TrendContextRole.BIAS, series(TrendContextRole.BIAS,
                List.of(realCandle("bias", "4H")), CUTOFF_AT));
        freshnessRoles.put(TrendContextRole.SETUP, seriesWithMetadata(TrendContextRole.SETUP,
                List.of(realCandle("setup", "1H")), CUTOFF_AT,
                "1H", MARKET_ID, "KRAKEN", "BTC/EUR", false, false));
        String alteredFreshness = TrendContextAssessmentInput.accept(
                new TrendContextAssessmentInput.Values(MARKET_ID, "KRAKEN", "BTC/EUR",
                        ASSESSMENT_AT, CUTOFF_AT, profile(), "rules-1", freshnessRoles)).fingerprint();
        assertThat(alteredFreshness).isNotEqualTo(base);
    }

    private TrendContextAssessmentInput input(
            TrendContextProfile expectedProfile,
            TrendContextCandle bias,
            TrendContextCandle setup,
            Instant cutoff,
            String ruleVersion,
            TrendContextProfile actualProfile
    ) {
        Objects.requireNonNull(expectedProfile, "expectedProfile is required");
        return inputWithRoleOrder(actualProfile, List.of(setup), false,
                new TrendContextCandle[]{bias}, cutoff, ruleVersion);
    }

    private TrendContextAssessmentInput input(
            TrendContextProfile profile,
            TrendContextCandle bias,
            TrendContextCandle setup
    ) {
        return input(profile, bias, setup == null ? List.of() : List.of(setup));
    }

    private TrendContextAssessmentInput input(
            TrendContextProfile profile,
            TrendContextCandle bias,
            List<TrendContextCandle> setup
    ) {
        return inputWithRoleOrder(profile, setup, false, new TrendContextCandle[]{bias});
    }

    private TrendContextAssessmentInput inputWithTrigger(
            TrendContextProfile profile,
            TrendContextCandle bias,
            TrendContextCandle setup,
            TrendContextCandle trigger
    ) {
        EnumMap<TrendContextRole, TrendContextRoleSeries> roles = new EnumMap<>(TrendContextRole.class);
        roles.put(TrendContextRole.BIAS, series(TrendContextRole.BIAS, List.of(bias), CUTOFF_AT));
        roles.put(TrendContextRole.SETUP, series(TrendContextRole.SETUP, List.of(setup), CUTOFF_AT));
        roles.put(TrendContextRole.TRIGGER, series(TrendContextRole.TRIGGER,
                List.of(trigger), CUTOFF_AT));
        return TrendContextAssessmentInput.accept(new TrendContextAssessmentInput.Values(
                MARKET_ID, "KRAKEN", "BTC/EUR", ASSESSMENT_AT, CUTOFF_AT,
                profile, "rules-1", roles));
    }

    private TrendContextAssessmentInput inputWithRoleOrder(
            TrendContextProfile profile,
            List<TrendContextCandle> setup,
            boolean setupFirst,
            TrendContextCandle... biasValues
    ) {
        return inputWithRoleOrder(profile, setup, setupFirst, biasValues, CUTOFF_AT, "rules-1");
    }

    private TrendContextAssessmentInput inputWithRoleOrder(
            TrendContextProfile profile,
            List<TrendContextCandle> setup,
            boolean setupFirst,
            TrendContextCandle[] biasValues,
            Instant cutoff,
            String ruleVersion
    ) {
        EnumMap<TrendContextRole, TrendContextRoleSeries> roles = new EnumMap<>(TrendContextRole.class);
        if (biasValues.length > 0 && biasValues[0] != null) {
            TrendContextRoleSeries bias = series(
                    TrendContextRole.BIAS, List.of(biasValues[0]), cutoff);
            roles.put(TrendContextRole.BIAS, bias);
        }
        if (!setup.isEmpty()) {
            TrendContextRoleSeries setupSeries = series(TrendContextRole.SETUP, setup, cutoff);
            if (setupFirst) {
                roles.put(TrendContextRole.SETUP, setupSeries);
            } else {
                roles.put(TrendContextRole.SETUP, setupSeries);
            }
        }
        return TrendContextAssessmentInput.accept(new TrendContextAssessmentInput.Values(
                MARKET_ID, "KRAKEN", "BTC/EUR", ASSESSMENT_AT, cutoff,
                profile, ruleVersion, roles));
    }

    private TrendContextRoleSeries series(
            TrendContextRole role,
            List<TrendContextCandle> candles,
            Instant cutoff
    ) {
        TrendContextCandle first = candles.stream()
                .min(java.util.Comparator.comparing(TrendContextCandle::openTime))
                .orElseThrow();
        List<String> exclusions = new ArrayList<>();
        List<TrendContextGapFinding> gaps = new ArrayList<>();
        for (TrendContextCandle candle : candles) {
            if (!candle.closed()) exclusions.add(candle.sourceId() + ":OPEN_CANDLE_EXCLUDED");
            if (candle.synthetic()) {
                exclusions.add(candle.sourceId() + ":SYNTHETIC_DATA_EXCLUDED");
                gaps.add(new TrendContextGapFinding(role, "SYNTHETIC_DATA_EXCLUDED",
                        candle.openTime(), candle.closeTime(), "Synthetic candle excluded"));
            }
            if (candle.closeTime().isAfter(cutoff)) {
                exclusions.add(candle.sourceId() + ":CUTOFF_EXCLUDED");
            }
        }
        return seriesWithMetadata(role, candles, cutoff, first.interval(), MARKET_ID,
                first.provider(), first.symbol(), true, true);
    }

    private TrendContextRoleSeries seriesWithMetadata(
            TrendContextRole role,
            List<TrendContextCandle> candles,
            Instant cutoff,
            String interval,
            UUID sourceMarketId,
            String sourceProvider,
            String sourceSymbol,
            boolean roleAvailable,
            boolean eligibleEvidencePresent
    ) {
        TrendContextCandle first = candles.stream()
                .min(java.util.Comparator.comparing(TrendContextCandle::openTime))
                .orElseThrow();
        TrendContextCandle last = candles.stream()
                .max(java.util.Comparator.comparing(TrendContextCandle::openTime))
                .orElseThrow();
        List<String> exclusions = new ArrayList<>();
        List<TrendContextGapFinding> gaps = new ArrayList<>();
        for (TrendContextCandle candle : candles) {
            if (!candle.closed()) exclusions.add(candle.sourceId() + ":OPEN_CANDLE_EXCLUDED");
            if (candle.synthetic()) {
                exclusions.add(candle.sourceId() + ":SYNTHETIC_DATA_EXCLUDED");
                gaps.add(new TrendContextGapFinding(role, "SYNTHETIC_DATA_EXCLUDED",
                        candle.openTime(), candle.closeTime(), "Synthetic candle excluded"));
            }
            if (candle.closeTime().isAfter(cutoff)) {
                exclusions.add(candle.sourceId() + ":CUTOFF_EXCLUDED");
            }
        }
        return TrendContextRoleSeries.of(new TrendContextRoleSeries.Values(
                role, interval, candles, exclusions, gaps,
                new TrendContextSourceReference("market-data", sourceProvider, sourceMarketId,
                        sourceSymbol, role, interval, first.openTime(), first.openTime(),
                        first.sourceOccurredAt(), first.fetchedAt(), "snapshot", "digest"),
                new TrendContextFreshness(Duration.ofHours(1), first.closeTime(),
                        first.sourceOccurredAt(), first.fetchedAt(), ASSESSMENT_AT,
                        roleAvailable, eligibleEvidencePresent), cutoff));
    }

    private TrendContextProfile profile() { return profile(1, "1.0.0"); }

    private TrendContextProfile profile(int minimumSetup, String version) {
        EnumMap<TrendContextRole, TrendContextRoleDefinition> roles = roles("4H", "1H");
        roles.put(TrendContextRole.SETUP, new TrendContextRoleDefinition(
                TrendContextRole.SETUP, "1H", Duration.ofHours(1), true, minimumSetup,
                Math.max(minimumSetup, 2)));
        return TrendContextProfile.conservativeSwingV1(version, roles);
    }

    private TrendContextProfile profileWithTrigger() {
        EnumMap<TrendContextRole, TrendContextRoleDefinition> roles = roles("4H", "1H");
        roles.put(TrendContextRole.TRIGGER, new TrendContextRoleDefinition(
                TrendContextRole.TRIGGER, "15M", Duration.ofMinutes(15), false, 1, 1));
        return TrendContextProfile.conservativeSwingV1(roles);
    }

    private EnumMap<TrendContextRole, TrendContextRoleDefinition> roles(
            String biasInterval,
            String setupInterval
    ) {
        EnumMap<TrendContextRole, TrendContextRoleDefinition> roles = new EnumMap<>(TrendContextRole.class);
        roles.put(TrendContextRole.BIAS, new TrendContextRoleDefinition(
                TrendContextRole.BIAS, biasInterval, duration(biasInterval), true, 1, 1));
        roles.put(TrendContextRole.SETUP, new TrendContextRoleDefinition(
                TrendContextRole.SETUP, setupInterval, duration(setupInterval), true, 1, 1));
        return roles;
    }

    private Duration duration(String interval) {
        return switch (interval) {
            case "4H" -> Duration.ofHours(4);
            case "1H" -> Duration.ofHours(1);
            case "15M" -> Duration.ofMinutes(15);
            default -> throw new IllegalArgumentException("Unsupported test interval");
        };
    }

    private TrendContextCandle realCandle(String sourceId, String interval) {
        return candle(interval, "100", "105", "95", "102", true, false,
                sourceId, CUTOFF_AT.minus(Duration.ofHours(1)), CUTOFF_AT, FETCHED_AT);
    }

    private TrendContextCandle candle(
            String interval,
            String open,
            String high,
            String low,
            String close,
            boolean closed,
            boolean synthetic,
            String sourceId,
            Instant openTime,
            Instant closeTime,
            Instant fetchedAt
    ) {
        return new TrendContextCandle("KRAKEN", "BTC/EUR", interval, openTime, closeTime,
                new BigDecimal(open), new BigDecimal(high), new BigDecimal(low),
                new BigDecimal(close), BigDecimal.TEN, closed, synthetic, sourceId,
                closeTime, fetchedAt);
    }
}
