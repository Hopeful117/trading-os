package com.hope.trading.market_intelligence.domain.trendcontext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrendContextEngineTest {
    private static final UUID MARKET = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant START = Instant.parse("2026-09-20T00:00:00Z");
    private static final Instant ASSESSMENT = START.plus(Duration.ofHours(79));

    @Test
    void strictStructureIsIndependentOfIndicatorsAndUsesProtectedLevel() {
        TrendContextAssessment assessment = assess(false, false, false, 80);

        TrendContextTimeframeAssessment setup = assessment.roleAssessments().get(TrendContextRole.SETUP);
        assertThat(setup.direction()).isEqualTo(TrendDirection.UP);
        assertThat(setup.regime()).isEqualTo(TrendRegime.TRENDING);
        assertThat(setup.highRelation()).isEqualTo(SwingRelation.HH);
        assertThat(setup.lowRelation()).isEqualTo(SwingRelation.HL);
        assertThat(setup.protectedLevel()).isNotNull();
        assertThat(setup.protectedLevel().price()).isEqualByComparingTo("90");
        assertThat(setup.atr().available()).isTrue();
        assertThat(setup.ema().available()).isTrue();
    }

    @Test
    void equalPricesNeverBecomeStrictPivots() {
        TrendContextAssessment assessment = assess(true, false, false, 80);
        TrendContextTimeframeAssessment setup = assessment.roleAssessments().get(TrendContextRole.SETUP);
        assertThat(setup.direction()).isNotEqualTo(TrendDirection.UP);
        assertThat(setup.highRelation()).isNotEqualTo(SwingRelation.HH);
    }

    @Test
    void wickOnlyBreakIsAWarningAndDoesNotChangeDirection() {
        TrendContextAssessment assessment = assess(false, true, false, 80);
        TrendContextTimeframeAssessment setup = assessment.roleAssessments().get(TrendContextRole.SETUP);
        assertThat(setup.direction()).isEqualTo(TrendDirection.UP);
        assertThat(setup.regime()).isEqualTo(TrendRegime.TRENDING);
        assertThat(setup.structuralBreak().status()).isEqualTo(BreakStatus.UNCONFIRMED_BEARISH_BREAK);
    }

    @Test
    void confirmedBreakIsTransitionAndNotAnInstantReversal() {
        TrendContextAssessment assessment = assess(false, false, true, 80);
        TrendContextTimeframeAssessment setup = assessment.roleAssessments().get(TrendContextRole.SETUP);
        assertThat(setup.direction()).isEqualTo(TrendDirection.UP);
        assertThat(setup.regime()).isEqualTo(TrendRegime.TRANSITIONING);
        assertThat(setup.phase()).isEqualTo(TrendPhase.TRANSITION);
        assertThat(assessment.attention()).isEqualTo(TrendAttention.CONTEXTUALLY_DANGEROUS);
    }

    @Test
    void triggerCannotReverseBiasAndIndicatorCannotCreateDirection() {
        TrendContextAssessment noTrigger = assess(false, false, false, 80);
        assertThat(noTrigger.alignment()).isEqualTo(TrendTimeframeAlignment.TRIGGER_UNAVAILABLE);
        assertThat(noTrigger.attention()).isEqualTo(TrendAttention.WATCH);

        TrendContextAssessment shortInput = assess(false, false, false, 4);
        assertThat(shortInput.roleAssessments().get(TrendContextRole.SETUP).direction())
                .isEqualTo(TrendDirection.UNKNOWN);
        assertThat(shortInput.attention()).isEqualTo(TrendAttention.NO_SETUP);
    }

    @Test
    void syntheticAndOpenCandlesAreNeverUsedAsMarketEvidence() {
        TrendContextAssessment assessment = assess(false, false, false, 80, true);
        TrendContextTimeframeAssessment setup = assessment.roleAssessments().get(TrendContextRole.SETUP);
        assertThat(setup.swings()).noneMatch(s -> s.pivotSourceId().equals("synthetic"));
        assertThat(assessment.exclusions()).extracting(TrendContextExclusion::code)
                .contains("SYNTHETIC_DATA_EXCLUDED", "OPEN_CANDLE_EXCLUDED");
    }

    @Test
    void cutOffReplayAndFingerprintAreDeterministic() {
        TrendContextAssessment first = assess(false, false, false, 80);
        TrendContextAssessment replay = assess(false, false, false, 80);
        TrendContextAssessment withFuture = assess(false, false, false, 80, false, true);

        assertThat(first.fingerprint()).isEqualTo(replay.fingerprint());
        assertThat(first.fingerprint()).isEqualTo(first.assessmentFingerprint());
        assertThat(withFuture.roleAssessments().get(TrendContextRole.SETUP).direction())
                .isEqualTo(first.roleAssessments().get(TrendContextRole.SETUP).direction());
        assertThat(withFuture.roleAssessments().get(TrendContextRole.SETUP).swings().stream()
                .map(ConfirmedSwing::pivotSourceId).toList())
                .isEqualTo(first.roleAssessments().get(TrendContextRole.SETUP).swings().stream()
                        .map(ConfirmedSwing::pivotSourceId).toList());
        assertThat(withFuture.attention()).isEqualTo(first.attention());
        assertThat(withFuture.fingerprint()).isNotEqualTo(first.fingerprint());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UP", "DOWN", "PULLBACK", "EXTENDED", "TRANSITION", "WICK", "EQUAL", "CONFLICT"})
    void canonicalScenarioFamiliesRemainTypedAndScoreless(String scenario) {
        TrendContextAssessment assessment = assess(false, scenario.equals("WICK"), scenario.equals("TRANSITION"), 80);
        assertThat(assessment.attention()).isNotNull();
        assertThat(assessment.direction()).isNotNull();
        assertThat(assessment.findings()).allSatisfy(finding -> assertThat(finding.code()).isNotBlank());
    }

    private TrendContextAssessment assess(boolean equalHigh, boolean wick, boolean breakClose, int count) {
        return assess(equalHigh, wick, breakClose, count, false, false);
    }

    private TrendContextAssessment assess(boolean equalHigh, boolean wick, boolean breakClose, int count, boolean excluded) {
        return assess(equalHigh, wick, breakClose, count, excluded, false);
    }

    private TrendContextAssessment assess(boolean equalHigh, boolean wick, boolean breakClose, int count, boolean excluded, boolean future) {
        TrendContextProfile profile = profile();
        List<TrendContextCandle> candles = candles("SETUP", "1H", count, equalHigh, wick, breakClose, excluded, future);
        Map<TrendContextRole, TrendContextRoleSeries> roles = new EnumMap<>(TrendContextRole.class);
        roles.put(TrendContextRole.BIAS, series(TrendContextRole.BIAS, "4H", candles("BIAS", "4H", count, equalHigh, false, false, false, false)));
        roles.put(TrendContextRole.SETUP, series(TrendContextRole.SETUP, "1H", candles));
        TrendContextAssessmentInput input = TrendContextAssessmentInput.accept(MARKET, "KRAKEN", "BTC/EUR",
                ASSESSMENT, START.plus(Duration.ofHours(79)), profile, "rules-1", roles);
        return new TrendContextEngine().assess(input);
    }

    private TrendContextProfile profile() {
        EnumMap<TrendContextRole, TrendContextRoleDefinition> roles = new EnumMap<>(TrendContextRole.class);
        roles.put(TrendContextRole.BIAS, new TrendContextRoleDefinition(TrendContextRole.BIAS, "4H", Duration.ofHours(4), true, 1, 1));
        roles.put(TrendContextRole.SETUP, new TrendContextRoleDefinition(TrendContextRole.SETUP, "1H", Duration.ofHours(1), true, 1, 1));
        return TrendContextProfile.conservativeSwingV1(roles);
    }

    private List<TrendContextCandle> candles(String prefix, String interval, int count, boolean equalHigh,
                                               boolean wick, boolean breakClose, boolean excluded, boolean future) {
        java.util.ArrayList<TrendContextCandle> result = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            BigDecimal high = bd("105"), low = bd("96"), close = bd("100");
            if (i == 5) high = bd("110");
            if (i == 10) high = bd(equalHigh ? "110" : "115");
            if (i == 7) low = bd("90");
            if (i == 12) low = bd("95");
            if (i == count - 2 && breakClose) { close = bd("89"); low = bd("89"); }
            if (i == count - 2 && wick) low = bd("89");
            boolean synthetic = excluded && i == 10;
            boolean closed = !(excluded && i == 12);
            Instant open = START.plus(Duration.ofHours(i));
            Instant closeTime = open.plus(Duration.ofHours(1));
            if (future && i == count - 1) closeTime = ASSESSMENT.plus(Duration.ofHours(2));
            result.add(candle(prefix + '-' + i, interval, open, closeTime, high, low, close, closed, synthetic));
        }
        return result;
    }

    private TrendContextRoleSeries series(TrendContextRole role, String interval, List<TrendContextCandle> candles) {
        TrendContextCandle first = candles.getFirst(), last = candles.getLast();
        return TrendContextRoleSeries.of(role, interval, candles, List.of(), List.of(),
                new TrendContextSourceReference("market-data", "KRAKEN", MARKET, "BTC/EUR", role, interval,
                        first.openTime(), last.closeTime(), first.sourceOccurredAt(), first.fetchedAt(), "snapshot", "digest"),
                new TrendContextFreshness(Duration.ofHours(1), last.closeTime(), last.sourceOccurredAt(), last.fetchedAt(), ASSESSMENT, true, true),
                ASSESSMENT);
    }

    private TrendContextCandle candle(String id, String interval, Instant open, Instant close,
                                      BigDecimal high, BigDecimal low, BigDecimal price, boolean closed, boolean synthetic) {
        return new TrendContextCandle("KRAKEN", "BTC/EUR", interval, open, close, price, high, low, price,
                BigDecimal.TEN, closed, synthetic, id, close, close);
    }

    private BigDecimal bd(String value) { return new BigDecimal(value); }
}
