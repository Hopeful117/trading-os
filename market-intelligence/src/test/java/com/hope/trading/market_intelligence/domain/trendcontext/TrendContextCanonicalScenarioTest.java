package com.hope.trading.market_intelligence.domain.trendcontext;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrendContextCanonicalScenarioTest {
    private static final UUID MARKET = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant START = Instant.parse("2026-09-20T00:00:00Z");
    private static final Instant ASSESSMENT = START.plus(Duration.ofHours(79));
    private static final Instant CUTOFF = ASSESSMENT;

    @Test
    void scenario01AlignedUp() {
        TrendContextAssessment value = assess(Shape.UP, Shape.UP, Mode.NORMAL, Shape.UP, true);
        assertAligned(value, TrendDirection.UP, TrendTimeframeAlignment.ALIGNED_UP, TrendAttention.CONTEXTUALLY_ATTRACTIVE);
    }

    @Test
    void scenario02AlignedDown() {
        TrendContextAssessment value = assess(Shape.DOWN, Shape.DOWN, Mode.NORMAL, Shape.DOWN, true);
        assertAligned(value, TrendDirection.DOWN, TrendTimeframeAlignment.ALIGNED_DOWN, TrendAttention.CONTEXTUALLY_ATTRACTIVE);
    }

    @Test
    void scenario03BullishPullback() {
        TrendContextAssessment value = assess(Shape.UP, Shape.UP, Mode.PULLBACK, Shape.UP, true);
        assertThat(value.phase()).isEqualTo(TrendPhase.PULLBACK);
        assertThat(value.alignment()).isEqualTo(TrendTimeframeAlignment.PULLBACK_WITHIN_UP_BIAS);
        assertThat(value.attention()).isEqualTo(TrendAttention.CONTEXTUALLY_ATTRACTIVE);
    }

    @Test
    void scenario04BearishPullback() {
        TrendContextAssessment value = assess(Shape.DOWN, Shape.DOWN, Mode.PULLBACK, Shape.DOWN, true);
        assertThat(value.phase()).isEqualTo(TrendPhase.PULLBACK);
        assertThat(value.alignment()).isEqualTo(TrendTimeframeAlignment.PULLBACK_WITHIN_DOWN_BIAS);
        assertThat(value.attention()).isEqualTo(TrendAttention.CONTEXTUALLY_ATTRACTIVE);
    }

    @Test
    void scenario05BullishExtension() {
        TrendContextAssessment value = assess(Shape.UP, Shape.UP, Mode.EXTENSION, Shape.UP, true);
        assertThat(value.roleAssessments().get(TrendContextRole.SETUP).extension().extended()).isTrue();
        assertThat(value.phase()).isEqualTo(TrendPhase.EXTENDED);
        assertThat(value.attention()).isEqualTo(TrendAttention.CONTEXTUALLY_DANGEROUS);
        assertThat(value.findings()).extracting(TrendContextFinding::code).contains("EXTENDED_LOCATION");
    }

    @Test
    void scenario06BearishExtension() {
        TrendContextAssessment value = assess(Shape.DOWN, Shape.DOWN, Mode.EXTENSION, Shape.DOWN, true);
        assertThat(value.roleAssessments().get(TrendContextRole.SETUP).extension().extended()).isTrue();
        assertThat(value.phase()).isEqualTo(TrendPhase.EXTENDED);
        assertThat(value.attention()).isEqualTo(TrendAttention.CONTEXTUALLY_DANGEROUS);
    }

    @Test
    void scenario07BearishBreakFromUp() {
        TrendContextAssessment value = assess(Shape.UP, Shape.UP, Mode.BREAK, Shape.UP, true);
        assertThat(value.roleAssessments().get(TrendContextRole.SETUP).direction()).isEqualTo(TrendDirection.UP);
        assertThat(value.roleAssessments().get(TrendContextRole.SETUP).protectedLevel()).isNotNull();
        assertThat(value.roleAssessments().get(TrendContextRole.SETUP).structuralBreak().status())
                .isEqualTo(BreakStatus.CONFIRMED_BEARISH_BREAK);
        assertThat(value.roleAssessments().get(TrendContextRole.SETUP).regime()).isEqualTo(TrendRegime.TRANSITIONING);
        assertThat(value.phase()).isEqualTo(TrendPhase.TRANSITION);
        assertThat(value.alignment()).isEqualTo(TrendTimeframeAlignment.BIAS_TRANSITION);
        assertThat(value.attention()).isEqualTo(TrendAttention.CONTEXTUALLY_DANGEROUS);
    }

    @Test
    void scenario08BullishBreakFromDown() {
        TrendContextAssessment value = assess(Shape.DOWN, Shape.DOWN, Mode.BREAK, Shape.DOWN, true);
        assertThat(value.roleAssessments().get(TrendContextRole.SETUP).direction()).isEqualTo(TrendDirection.DOWN);
        assertThat(value.roleAssessments().get(TrendContextRole.SETUP).protectedLevel()).isNotNull();
        assertThat(value.roleAssessments().get(TrendContextRole.SETUP).structuralBreak().status())
                .isEqualTo(BreakStatus.CONFIRMED_BULLISH_BREAK);
        assertThat(value.roleAssessments().get(TrendContextRole.SETUP).regime()).isEqualTo(TrendRegime.TRANSITIONING);
        assertThat(value.attention()).isEqualTo(TrendAttention.CONTEXTUALLY_DANGEROUS);
    }

    @Test
    void scenario09OppositeStructureAfterBreak() {
        TrendContextAssessment value = assess(Shape.DOWN, Shape.UP, Mode.OPPOSITE_AFTER_BREAK, Shape.DOWN, true);
        TrendContextTimeframeAssessment setup = value.roleAssessments().get(TrendContextRole.SETUP);
        assertThat(setup.direction()).isEqualTo(TrendDirection.DOWN);
        assertThat(setup.regime()).isEqualTo(TrendRegime.TRENDING);
        assertThat(setup.protectedLevel()).isNotNull();
        assertThat(setup.protectedLevel().type()).isEqualTo(SwingType.HIGH);
        assertThat(value.attention()).isIn(TrendAttention.WATCH, TrendAttention.CONTEXTUALLY_ATTRACTIVE);
    }

    @Test
    void scenario10WickOnlyBreach() {
        TrendContextAssessment value = assess(Shape.UP, Shape.UP, Mode.WICK, Shape.UP, true);
        TrendContextTimeframeAssessment setup = value.roleAssessments().get(TrendContextRole.SETUP);
        assertThat(setup.structuralBreak().status()).isEqualTo(BreakStatus.UNCONFIRMED_BEARISH_BREAK);
        assertThat(setup.direction()).isEqualTo(TrendDirection.UP);
        assertThat(setup.regime()).isEqualTo(TrendRegime.TRENDING);
        assertThat(value.findings()).extracting(TrendContextFinding::code)
                .contains(BreakStatus.UNCONFIRMED_BEARISH_BREAK.name());
    }

    @Test
    void scenario11FailedBreakReclaim() {
        TrendContextAssessment value = assess(Shape.UP, Shape.UP, Mode.RECLAIM, Shape.UP, true);
        TrendContextTimeframeAssessment setup = value.roleAssessments().get(TrendContextRole.SETUP);
        assertThat(setup.direction()).isEqualTo(TrendDirection.UP);
        assertThat(setup.phase()).isEqualTo(TrendPhase.TRANSITION);
        assertThat(setup.structuralBreak().reclaimCandle()).isNotNull();
        assertThat(value.findings()).extracting(TrendContextFinding::code).contains("FAILED_BREAK_RECLAIM");
        assertThat(value.attention()).isEqualTo(TrendAttention.CONTEXTUALLY_DANGEROUS);
    }

    @Test
    void scenario12NonDirectionalStructure() {
        TrendContextAssessment value = assess(Shape.UP, Shape.NEUTRAL, Mode.NORMAL, Shape.UP, true);
        TrendContextTimeframeAssessment setup = value.roleAssessments().get(TrendContextRole.SETUP);
        assertThat(setup.direction()).isEqualTo(TrendDirection.NEUTRAL);
        assertThat(setup.regime()).isEqualTo(TrendRegime.NON_DIRECTIONAL);
        assertThat(setup.phase()).isEqualTo(TrendPhase.UNDETERMINED);
        assertThat(value.alignment()).isEqualTo(TrendTimeframeAlignment.INSUFFICIENT_DIRECTION);
        assertThat(value.attention()).isEqualTo(TrendAttention.NO_SETUP);
    }

    @Test
    void scenario13EqualNeighborPricesPreventPivots() {
        TrendContextAssessment value = assess(Shape.EQUAL_NEIGHBORS, Shape.EQUAL_NEIGHBORS, Mode.NORMAL, Shape.UP, true);
        TrendContextTimeframeAssessment setup = value.roleAssessments().get(TrendContextRole.SETUP);
        assertThat(setup.swings()).isEmpty();
        assertThat(setup.findings()).extracting(TrendContextFinding::code).contains("INSUFFICIENT_SWINGS");
        assertThat(value.attention()).isEqualTo(TrendAttention.NO_SETUP);
    }

    @Test
    void scenario14BiasSetupConflict() {
        TrendContextAssessment value = assess(Shape.UP, Shape.DOWN, Mode.NORMAL, Shape.DOWN, true);
        assertThat(value.alignment()).isEqualTo(TrendTimeframeAlignment.CONFLICTING);
        assertThat(value.contradictions()).extracting(TrendContextContradiction::code).contains("TIMEFRAME_CONTRADICTION");
        assertThat(value.attention()).isEqualTo(TrendAttention.NO_SETUP);
    }

    @Test
    void scenario15ControlledPullbackWithinBias() {
        TrendContextAssessment value = assess(Shape.UP, Shape.UP, Mode.PULLBACK, Shape.UP, true);
        assertThat(value.alignment()).isEqualTo(TrendTimeframeAlignment.PULLBACK_WITHIN_UP_BIAS);
        assertThat(value.attention()).isEqualTo(TrendAttention.CONTEXTUALLY_ATTRACTIVE);
    }

    @Test
    void scenario16TriggerContradiction() {
        TrendContextAssessment value = assess(Shape.UP, Shape.UP, Mode.PULLBACK, Shape.DOWN, true);
        assertThat(value.alignment()).isEqualTo(TrendTimeframeAlignment.TRIGGER_CONTRADICTION);
        assertThat(value.contradictions()).extracting(TrendContextContradiction::code).contains("TRIGGER_CONTRADICTION");
        assertThat(value.attention()).isEqualTo(TrendAttention.CONTEXTUALLY_DANGEROUS);
    }

    @Test
    void scenario17StaleSetup() {
        TrendContextAssessment value = assess(Shape.UP, Shape.UP, Mode.STALE, Shape.UP, true);
        assertThat(value.roleAssessments().get(TrendContextRole.SETUP).fresh()).isFalse();
        assertThat(value.findings()).extracting(TrendContextFinding::code).contains("ROLE_STALE");
        assertThat(value.attention()).isEqualTo(TrendAttention.NO_SETUP);
    }

    @Test
    void scenario18OptionalTriggerAbsent() {
        TrendContextAssessment value = assess(Shape.UP, Shape.UP, Mode.NORMAL, null, false);
        assertThat(value.alignment()).isEqualTo(TrendTimeframeAlignment.TRIGGER_UNAVAILABLE);
        assertThat(value.attention()).isEqualTo(TrendAttention.WATCH);
    }

    @Test
    void optionalTriggerStalenessIsUnavailableAndCannotBlockRequiredContext() {
        TrendContextAssessment value = assess(Shape.UP, Shape.UP, Mode.TRIGGER_STALE, Shape.UP, true);
        assertThat(value.alignment()).isEqualTo(TrendTimeframeAlignment.TRIGGER_UNAVAILABLE);
        assertThat(value.attention()).isEqualTo(TrendAttention.WATCH);
    }

    @Test
    void scenario19MissingBiasIsRejectedByInputContract() {
        assertThatThrownBy(() -> input(Shape.UP, null, Mode.NORMAL, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REQUIRED_ROLE_MISSING");
    }

    @Test
    void scenario20InsufficientSwings() {
        TrendContextAssessment value = assess(Shape.UP, Shape.UP, Mode.INSUFFICIENT_SWINGS, Shape.UP, false);
        assertThat(value.roleAssessments().get(TrendContextRole.SETUP).findings())
                .extracting(TrendContextFinding::code).contains("INSUFFICIENT_SWINGS");
        assertThat(value.attention()).isEqualTo(TrendAttention.NO_SETUP);
    }

    @Test
    void scenario21SyntheticPivotIsExcluded() {
        TrendContextAssessment value = assess(Shape.UP, Shape.UP, Mode.SYNTHETIC, Shape.UP, false);
        assertThat(value.exclusions()).extracting(TrendContextExclusion::code).contains("SYNTHETIC_DATA_EXCLUDED");
        assertThat(value.attention()).isEqualTo(TrendAttention.NO_SETUP);
    }

    @Test
    void scenario22GapBlocksPivotWindow() {
        TrendContextAssessment value = assess(Shape.UP, Shape.UP, Mode.GAP, Shape.UP, false);
        assertThat(value.exclusions()).extracting(TrendContextExclusion::code).contains("GAP_IN_HISTORY");
        assertThat(value.roleAssessments().get(TrendContextRole.SETUP).direction())
                .isIn(TrendDirection.UNKNOWN, TrendDirection.NEUTRAL);
        assertThat(value.attention()).isEqualTo(TrendAttention.NO_SETUP);
    }

    @Test
    void scenario23AbnormalAtrIsDangerous() {
        TrendContextAssessment value = assess(Shape.UP, Shape.UP, Mode.ABNORMAL_ATR, Shape.UP, true);
        assertThat(value.roleAssessments().get(TrendContextRole.SETUP).atr().abnormal()).isTrue();
        assertThat(value.findings()).extracting(TrendContextFinding::code).contains("ABNORMAL_VOLATILITY");
        assertThat(value.attention()).isEqualTo(TrendAttention.CONTEXTUALLY_DANGEROUS);
    }

    @Test
    void scenario24ReplayIsIdentical() {
        TrendContextAssessment first = assess(Shape.UP, Shape.UP, Mode.NORMAL, Shape.UP, true);
        TrendContextAssessment second = assess(Shape.UP, Shape.UP, Mode.NORMAL, Shape.UP, true);
        assertThat(second.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(second.direction()).isEqualTo(first.direction());
        assertThat(second.regime()).isEqualTo(first.regime());
        assertThat(second.phase()).isEqualTo(first.phase());
        assertThat(second.alignment()).isEqualTo(first.alignment());
        assertThat(second.findings().stream().map(TrendContextFinding::code).toList())
                .isEqualTo(first.findings().stream().map(TrendContextFinding::code).toList());
        assertThat(second.exclusions().stream().map(TrendContextExclusion::code).toList())
                .isEqualTo(first.exclusions().stream().map(TrendContextExclusion::code).toList());
        assertThat(second.attention()).isEqualTo(first.attention());
    }

    @Test
    void materialResultsCarryTypedEvidenceLineage() {
        TrendContextAssessment value = assess(Shape.UP, Shape.UP, Mode.BREAK, Shape.UP, true);
        value.roleAssessments().values().forEach(role -> {
            assertThat(role.evidence()).isNotEmpty();
            role.swings().forEach(swing -> assertEvidence(swing.evidence()));
            role.suppressedSwings().forEach(swing -> assertEvidence(swing.evidence()));
            if (role.protectedLevel() != null) assertEvidence(role.protectedLevel().source().evidence());
            if (role.structuralBreak().evidence() != null) assertEvidence(role.structuralBreak().evidence());
            if (role.ema().evidence() != null) assertEvidence(role.ema().evidence());
            if (role.atr().evidence() != null) assertEvidence(role.atr().evidence());
            if (role.pullback().evidence() != null) assertEvidence(role.pullback().evidence());
            if (role.extension().evidence() != null) assertEvidence(role.extension().evidence());
            role.findings().forEach(finding -> finding.evidence().forEach(this::assertEvidence));
        });
        value.findings().forEach(finding -> assertThat(finding.evidence()).isNotEmpty());
        value.contradictions().forEach(contradiction -> assertThat(contradiction.evidence()).isNotEmpty());
        value.exclusions().forEach(exclusion -> assertEvidence(exclusion.evidence()));
        value.invalidations().forEach(invalidation -> assertEvidence(invalidation.evidence()));
    }

    private void assertEvidence(TrendContextEvidenceReference evidence) {
        assertThat(evidence.role()).isNotNull();
        assertThat(evidence.interval()).isNotBlank();
        assertThat(evidence.ruleId()).isNotBlank();
        assertThat(evidence.ruleVersion()).isEqualTo("rules-1");
        assertThat(evidence.profileVersion()).isEqualTo("1.0.0");
        assertThat(evidence.inputFingerprint()).isNotBlank();
        assertThat(evidence.cutOffAt()).isEqualTo(CUTOFF);
        assertThat(evidence.key()).isNotBlank();
    }

    private void assertAligned(TrendContextAssessment value, TrendDirection direction,
                                TrendTimeframeAlignment alignment, TrendAttention attention) {
        TrendContextTimeframeAssessment setup = value.roleAssessments().get(TrendContextRole.SETUP);
        assertThat(value.direction()).isEqualTo(direction);
        assertThat(value.regime()).isEqualTo(TrendRegime.TRENDING);
        assertThat(value.phase()).isEqualTo(TrendPhase.DIRECTIONAL);
        assertThat(value.alignment()).isEqualTo(alignment);
        assertThat(value.attention()).isEqualTo(attention);
        assertThat(setup.protectedLevel()).isNotNull();
        assertThat(setup.highRelation()).isNotNull();
        assertThat(setup.lowRelation()).isNotNull();
        assertThat(setup.ema().available()).isTrue();
        assertThat(setup.atr().available()).isTrue();
    }

    private TrendContextAssessment assess(Shape bias, Shape setup, Mode mode, Shape trigger, boolean withTrigger) {
        return new TrendContextEngine().assess(input(bias, setup, mode, trigger, withTrigger));
    }

    private TrendContextAssessmentInput input(Shape bias, Shape setup, Mode mode, Shape trigger, boolean withTrigger) {
        EnumMap<TrendContextRole, TrendContextRoleDefinition> definitions = new EnumMap<>(TrendContextRole.class);
        definitions.put(TrendContextRole.BIAS, definition(TrendContextRole.BIAS, "4H", Duration.ofHours(4), true));
        definitions.put(TrendContextRole.SETUP, definition(TrendContextRole.SETUP, "1H", Duration.ofHours(1), true));
        if (withTrigger) definitions.put(TrendContextRole.TRIGGER, definition(TrendContextRole.TRIGGER, "15M", Duration.ofMinutes(15), false));
        TrendContextProfile profile = TrendContextProfile.conservativeSwingV1(definitions);
        EnumMap<TrendContextRole, TrendContextRoleSeries> roles = new EnumMap<>(TrendContextRole.class);
        if (bias != null) roles.put(TrendContextRole.BIAS, series(TrendContextRole.BIAS, "4H", candles("bias", bias, Mode.NORMAL, 80), List.of()));
        if (setup != null) {
            int count = mode == Mode.INSUFFICIENT_SWINGS ? 4 : mode == Mode.STALE ? 75 : 80;
            List<TrendContextGapFinding> gaps = mode == Mode.GAP ? List.of(new TrendContextGapFinding(TrendContextRole.SETUP, "GAP_IN_HISTORY", START.plus(Duration.ofHours(8)), START.plus(Duration.ofHours(14)), "Pivot window gap")) : List.of();
            roles.put(TrendContextRole.SETUP, series(TrendContextRole.SETUP, "1H", candles("setup", setup, mode, count), gaps));
        }
        if (withTrigger && trigger != null) {
            int triggerCount = mode == Mode.TRIGGER_STALE ? 75 : 80;
            roles.put(TrendContextRole.TRIGGER, series(TrendContextRole.TRIGGER, "15M", candles("trigger", trigger, mode == Mode.TRIGGER_STALE ? Mode.STALE : Mode.NORMAL, triggerCount), List.of()));
        }
        return TrendContextAssessmentInput.accept(MARKET, "KRAKEN", "BTC/EUR", ASSESSMENT, CUTOFF, profile, "rules-1", roles);
    }

    private TrendContextRoleDefinition definition(TrendContextRole role, String interval, Duration duration, boolean required) {
        return new TrendContextRoleDefinition(role, interval, duration, required, 1, 1);
    }

    private TrendContextRoleSeries series(TrendContextRole role, String interval, List<TrendContextCandle> values, List<TrendContextGapFinding> gaps) {
        TrendContextCandle first = values.getFirst();
        TrendContextCandle last = values.getLast();
        List<String> exclusions = values.stream().filter(c -> c.synthetic()).map(c -> c.sourceId() + ":SYNTHETIC_DATA_EXCLUDED").toList();
        return TrendContextRoleSeries.of(role, interval, values, exclusions, gaps,
                new TrendContextSourceReference("market-data", "KRAKEN", MARKET, "BTC/EUR", role, interval,
                        first.openTime(), last.closeTime(), first.sourceOccurredAt(), first.fetchedAt(), "snapshot", "digest"),
                new TrendContextFreshness(Duration.ofHours(1), last.closeTime(), last.sourceOccurredAt(), last.fetchedAt(), ASSESSMENT, true, true), CUTOFF);
    }

    private List<TrendContextCandle> candles(String prefix, Shape shape, Mode mode, int count) {
        ArrayList<TrendContextCandle> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BigDecimal high = bd("105"), low = bd("96"), close = bd("100");
            if (shape == Shape.UP) { if (i == 10) high = bd("110"); if (i == 20) high = bd("115"); if (i == 15) low = bd("90"); if (i == 25) low = bd("95"); }
            if (shape == Shape.DOWN) { if (i == 10) high = bd("115"); if (i == 20) high = bd("110"); if (i == 15) low = bd("90"); if (i == 25) low = bd("85"); }
            if (shape == Shape.NEUTRAL) { if (i == 10) high = bd("115"); if (i == 20) high = bd("110"); if (i == 15) low = bd("90"); if (i == 25) low = bd("95"); }
            if (shape == Shape.EQUAL_NEIGHBORS) { if (i == 10 || i == 11) high = bd("115"); if (i == 15 || i == 16) low = bd("90"); }
            if (mode == Mode.PULLBACK && i == 30) { if (shape == Shape.UP) high = bd("120"); if (shape == Shape.DOWN) low = bd("80"); }
            if (mode == Mode.PULLBACK && i > 30) close = shape == Shape.UP ? bd(i == count - 2 ? "98" : "99") : bd(i == count - 2 ? "102" : "101");
            if (mode == Mode.EXTENSION && i == count - 2) { close = shape == Shape.UP ? bd("200") : bd("1"); high = close.add(bd("1")); low = shape == Shape.UP ? close.subtract(bd("1")) : close; }
            if (mode == Mode.BREAK || mode == Mode.RECLAIM || mode == Mode.WICK) {
                if (i == 50) {
                    if (shape == Shape.UP) { low = bd("89"); close = mode == Mode.WICK ? bd("100") : bd("89"); }
                    if (shape == Shape.DOWN) { high = bd("111"); close = mode == Mode.WICK ? bd("100") : bd("111"); }
                }
                if (mode == Mode.RECLAIM && i == 51) {
                    if (shape == Shape.UP) { close = bd("90"); low = bd("90"); }
                    if (shape == Shape.DOWN) { close = bd("110"); high = bd("110"); }
                }
                if (mode == Mode.BREAK && i > 50 && i <= 52) {
                    if (shape == Shape.UP) { close = bd("89"); low = bd("89"); }
                    if (shape == Shape.DOWN) { close = bd("111"); high = bd("111"); }
                }
            }
            if (mode == Mode.OPPOSITE_AFTER_BREAK) {
                if (i == 40) {
                    if (shape == Shape.UP) { low = bd("89"); close = bd("89"); }
                    if (shape == Shape.DOWN) { high = bd("111"); close = bd("111"); }
                }
                if (shape == Shape.UP) { if (i == 50) high = bd("108"); if (i == 60) high = bd("106"); if (i == 55) low = bd("85"); if (i == 65) low = bd("80"); }
                if (shape == Shape.DOWN) { if (i == 50) low = bd("88"); if (i == 60) low = bd("93"); if (i == 55) high = bd("115"); if (i == 65) high = bd("120"); }
                if (i > 40 && i <= 42) {
                    if (shape == Shape.UP) { close = bd("89"); low = bd("89"); }
                    if (shape == Shape.DOWN) { close = bd("111"); high = bd("111"); }
                }
            }
            if (mode == Mode.ABNORMAL_ATR && i == count - 2) { high = bd("500"); low = bd("1"); close = bd("100"); }
            boolean synthetic = mode == Mode.SYNTHETIC && i == 10;
            Instant open = START.plus(Duration.ofHours(i));
            Instant closeTime = open.plus(Duration.ofHours(1));
            if (mode == Mode.STALE && i == count - 1) closeTime = ASSESSMENT.minus(Duration.ofHours(3));
            result.add(new TrendContextCandle("KRAKEN", "BTC/EUR", prefix.equals("trigger") ? "15M" : prefix.equals("bias") ? "4H" : "1H", open, closeTime, close, high, low, close, BigDecimal.TEN, true, synthetic, prefix + '-' + i, closeTime, closeTime));
        }
        return result;
    }

    private BigDecimal bd(String value) { return new BigDecimal(value); }

    private enum Shape { UP, DOWN, NEUTRAL, EQUAL_NEIGHBORS }
    private enum Mode { NORMAL, PULLBACK, EXTENSION, BREAK, RECLAIM, WICK, OPPOSITE_AFTER_BREAK, STALE, TRIGGER_STALE, INSUFFICIENT_SWINGS, SYNTHETIC, GAP, ABNORMAL_ATR }
}
