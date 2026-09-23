package com.hope.trading.market_intelligence.domain.trendcontext;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        assertThatThrownBy(() -> input(profile(), (TrendContextCandle) null, realCandle("setup", "1H")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REQUIRED_ROLE_MISSING");
        assertThatThrownBy(() -> input(profile(), realCandle("bias", "4H"), (TrendContextCandle) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REQUIRED_ROLE_MISSING");
    }

    @Test
    void rejectsRoleIntervalMismatchAndInvalidOrdering() {
        assertThatThrownBy(() -> input(profile(), realCandle("bias", "4H"),
                realCandle("setup", "15M")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_INTERVAL_MISMATCH");

        assertThatThrownBy(() -> TrendContextProfile.conservativeSwingV1(
                roles("1H", "4H")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SETUP interval must be finer");

        assertThatThrownBy(() -> TrendContextProfile.conservativeSwingV1(
                roles("1H", "1H")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SETUP interval must be finer");
    }

    @Test
    void rejectsInvalidRoleParameters() {
        assertThatThrownBy(() -> new TrendContextRoleDefinition(
                TrendContextRole.BIAS, "4H", Duration.ZERO, true, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrendContextRoleDefinition(
                TrendContextRole.BIAS, "4H", Duration.ofHours(4), true, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrendContextProfile.conservativeSwingV1(
                "", roles("4H", "1H")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedOhlcAndImpossibleTimestamps() {
        TrendContextCandle malformed = candle("bad", "1H", "100", "99", "95", "98",
                true, false, "bad-ohlc", CUTOFF_AT.minus(Duration.ofHours(1)),
                CUTOFF_AT, CUTOFF_AT);
        assertThatThrownBy(() -> input(profile(), realCandle("bias", "4H"), malformed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID_OHLC");

        TrendContextCandle impossible = candle("bad-time", "1H", "100", "105", "95", "102",
                true, false, "bad-time", CUTOFF_AT, CUTOFF_AT.minusSeconds(1), CUTOFF_AT);
        assertThatThrownBy(() -> input(profile(), realCandle("bias", "4H"), impossible))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID_TIMESTAMP");
    }

    @Test
    void excludesOpenSyntheticAndCutoffCrossingEvidenceWithoutRejectingInput() {
        TrendContextCandle eligible = realCandle("eligible", "1H");
        TrendContextCandle open = candle("open", "1H", "100", "105", "95", "102",
                false, false, "open", CUTOFF_AT.minus(Duration.ofHours(2)),
                CUTOFF_AT.minus(Duration.ofHours(1)), FETCHED_AT);
        TrendContextCandle synthetic = candle("synthetic", "1H", "100", "100", "100", "100",
                true, true, "synthetic", CUTOFF_AT.minus(Duration.ofHours(3)),
                CUTOFF_AT.minus(Duration.ofHours(2)), FETCHED_AT);
        TrendContextCandle crossing = candle("crossing", "1H", "100", "105", "95", "102",
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
        TrendContextAssessmentInput first = input(profile(), realCandle("bias", "4H"),
                List.of(realCandle("setup-a", "1H"), realCandle("setup-b", "1H")));
        TrendContextAssessmentInput second = inputWithRoleOrder(profile(),
                List.of(realCandle("setup-b", "1H"), realCandle("setup-a", "1H")), true,
                realCandle("bias", "4H"));

        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
    }

    @Test
    void fingerprintChangesForMaterialEvidenceAndContractValues() {
        String base = input(profile(), realCandle("bias", "4H"), realCandle("setup", "1H"))
                .fingerprint();
        assertThat(input(profile(), realCandle("bias", "4H"),
                candle("setup", "1H", "101", "106", "96", "103", true, false,
                        "setup", CUTOFF_AT.minus(Duration.ofHours(1)), CUTOFF_AT, FETCHED_AT)).fingerprint())
                .isNotEqualTo(base);
        assertThat(input(profile(), realCandle("bias", "4H"),
                candle("setup", "1H", "100", "105", "95", "102", false, false,
                        "setup", CUTOFF_AT.minus(Duration.ofHours(1)), CUTOFF_AT, FETCHED_AT)).fingerprint())
                .isNotEqualTo(base);
        assertThat(input(profile(), realCandle("bias", "4H"),
                candle("setup", "1H", "100", "105", "95", "102", true, true,
                        "setup-synthetic", CUTOFF_AT.minus(Duration.ofHours(1)), CUTOFF_AT, FETCHED_AT)).fingerprint())
                .isNotEqualTo(base);
        assertThat(input(profile(), realCandle("bias", "4H"),
                candle("setup", "1H", "100", "105", "95", "102", true, false,
                        "setup-other", CUTOFF_AT.minus(Duration.ofHours(1)), CUTOFF_AT, FETCHED_AT)).fingerprint())
                .isNotEqualTo(base);
        assertThat(input(profile(), realCandle("bias", "4H"),
                candle("setup", "1H", "100", "105", "95", "102", true, false,
                        "setup", CUTOFF_AT.minus(Duration.ofHours(1)), CUTOFF_AT, FETCHED_AT.plusSeconds(1))).fingerprint())
                .isNotEqualTo(base);
        assertThat(input(profile(), realCandle("bias", "4H"), realCandle("setup", "1H"),
                CUTOFF_AT.minusSeconds(1), "rules-1", profile(1, "1.0.1")).fingerprint())
                .isNotEqualTo(base);
        assertThat(input(profile(), realCandle("bias", "4H"), realCandle("setup", "1H"),
                CUTOFF_AT, "rules-2", profile(1, "1.0.0")).fingerprint()).isNotEqualTo(base);
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

    private TrendContextAssessmentInput input(
            TrendContextProfile ignored,
            TrendContextCandle bias,
            TrendContextCandle setup,
            Instant cutoff,
            String ruleVersion,
            TrendContextProfile actualProfile
    ) {
        return inputWithRoleOrder(actualProfile, List.of(setup), false,
                new TrendContextCandle[]{bias}, cutoff, ruleVersion);
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
        return TrendContextAssessmentInput.accept(MARKET_ID, "KRAKEN", "BTC/EUR",
                ASSESSMENT_AT, CUTOFF_AT, profile, "rules-1", roles);
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
        return TrendContextAssessmentInput.accept(MARKET_ID, "KRAKEN", "BTC/EUR",
                ASSESSMENT_AT, cutoff, profile, ruleVersion, roles);
    }

    private TrendContextRoleSeries series(
            TrendContextRole role,
            List<TrendContextCandle> candles,
            Instant cutoff
    ) {
        TrendContextCandle first = candles.getFirst();
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
        return TrendContextRoleSeries.of(role, first.interval(), candles, exclusions, gaps,
                new TrendContextSourceReference("market-data", first.provider(), MARKET_ID,
                        first.symbol(), role, first.interval(), first.openTime(), first.openTime(),
                        first.sourceOccurredAt(), first.fetchedAt(), "snapshot", "digest"),
                new TrendContextFreshness(Duration.ofHours(1), first.closeTime(),
                        first.sourceOccurredAt(), first.fetchedAt(), ASSESSMENT_AT, true, true), cutoff);
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
        return candle(sourceId, interval, "100", "105", "95", "102", true, false,
                sourceId, CUTOFF_AT.minus(Duration.ofHours(1)), CUTOFF_AT, FETCHED_AT);
    }

    private TrendContextCandle candle(
            String label,
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
