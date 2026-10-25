package com.hope.trading.market_intelligence.application.thesis;

import com.hope.trading.market_intelligence.domain.trendcontext.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StructuralLevelObjectiveComposerTest {
    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");

    @Test
    void selectsNearestOpposingObjectiveForLongScenario() {
        TrendContextAssessment assessment = assessment(TrendDirection.UP, Map.of(
                TrendContextRole.BIAS, timeframe(TrendContextRole.BIAS, level(SwingType.HIGH, "120")),
                TrendContextRole.SETUP, timeframe(TrendContextRole.SETUP, level(SwingType.HIGH, "110"))));

        var result = new StructuralLevelObjectiveComposer().compose(
                assessment, new BigDecimal("100"), NOW, NOW);

        assertThat(result.levels()).extracting("role")
                .contains("OBJECTIVE");
        assertThat(result.levels().stream()
                .filter(level -> level.role().equals("OBJECTIVE"))
                .findFirst().orElseThrow().value()).isEqualByComparingTo("110");
        assertThat(result.levels()).noneMatch(level -> level.role().equals("OBSTACLE"));
    }

    @Test
    void selectsNearestOpposingObjectiveForShortScenario() {
        TrendContextAssessment assessment = assessment(TrendDirection.DOWN, Map.of(
                TrendContextRole.BIAS, timeframe(TrendContextRole.BIAS, level(SwingType.LOW, "80")),
                TrendContextRole.SETUP, timeframe(TrendContextRole.SETUP, level(SwingType.LOW, "90"))));

        var result = new StructuralLevelObjectiveComposer().compose(
                assessment, new BigDecimal("100"), NOW, NOW);

        assertThat(result.levels().stream()
                .filter(level -> level.role().equals("OBJECTIVE"))
                .findFirst().orElseThrow().value()).isEqualByComparingTo("90");
        assertThat(result.levels()).noneMatch(level -> level.role().equals("OBSTACLE"));
    }

    @Test
    void reportsMissingReferencePriceWithoutSelectingObjective() {
        TrendContextAssessment assessment = assessment(TrendDirection.UP, Map.of(
                TrendContextRole.BIAS, timeframe(TrendContextRole.BIAS, level(SwingType.HIGH, "120"))));

        var result = new StructuralLevelObjectiveComposer().compose(assessment, null, null, NOW);

        assertThat(result.levels()).noneMatch(level -> level.role().equals("OBJECTIVE"));
        assertThat(result.missingEvidence()).extracting("reference")
                .containsExactly("REFERENCE_PRICE");
        assertThat(result.reasons()).contains("STRUCTURAL_OBJECTIVE_REFERENCE_PRICE_UNAVAILABLE");
    }

    @Test
    void staleTimeframeLevelsCannotSupportStructuralClaims() {
        TrendContextAssessment assessment = assessment(TrendDirection.UP, Map.of(
                TrendContextRole.BIAS, timeframe(TrendContextRole.BIAS, false,
                        level(SwingType.HIGH, "120"))));

        var result = new StructuralLevelObjectiveComposer().compose(
                assessment, new BigDecimal("100"), NOW, NOW);

        assertThat(result.levels()).isEmpty();
        assertThat(result.missingEvidence()).extracting("reference")
                .containsExactly("STRUCTURAL_BIAS", "OBJECTIVE");
        assertThat(result.reasons()).contains("STRUCTURAL_LEVELS_STALE_BIAS");
    }

    private TrendContextAssessment assessment(
            TrendDirection direction,
            Map<TrendContextRole, TrendContextTimeframeAssessment> timeframes) {
        TrendContextAssessment assessment = mock(TrendContextAssessment.class);
        when(assessment.direction()).thenReturn(direction);
        when(assessment.assessmentAt()).thenReturn(NOW);
        when(assessment.cutOffAt()).thenReturn(NOW);
        when(assessment.inputFingerprint()).thenReturn("input");
        when(assessment.profileId()).thenReturn("profile");
        when(assessment.profileVersion()).thenReturn("profile-v1");
        when(assessment.fingerprint()).thenReturn("assessment-fingerprint");
        when(assessment.ruleVersion()).thenReturn("rules-v1");
        when(assessment.provider()).thenReturn("provider");
        when(assessment.symbol()).thenReturn("BTC/EUR");
        when(assessment.timeframes()).thenReturn(timeframes);
        when(assessment.invalidations()).thenReturn(List.of());
        return assessment;
    }

    private TrendContextTimeframeAssessment timeframe(
            TrendContextRole role, StructuralLevel level) {
        return timeframe(role, true, level);
    }

    private TrendContextTimeframeAssessment timeframe(
            TrendContextRole role, boolean fresh, StructuralLevel level) {
        TrendContextTimeframeAssessment timeframe = mock(TrendContextTimeframeAssessment.class);
        when(timeframe.role()).thenReturn(role);
        when(timeframe.interval()).thenReturn(role.name());
        when(timeframe.fresh()).thenReturn(fresh);
        if (level != null) {
            ConfirmedSwing confirmedSwing = swing(role);
            when(level.swing()).thenReturn(confirmedSwing);
        }
        when(timeframe.levels()).thenReturn(level == null ? List.of() : List.of(level));
        when(timeframe.protectedLevel()).thenReturn(null);
        return timeframe;
    }

    private StructuralLevel level(SwingType type, String price) {
        StructuralLevel level = mock(StructuralLevel.class);
        when(level.type()).thenReturn(type);
        when(level.price()).thenReturn(new BigDecimal(price));
        when(level.pivotTime()).thenReturn(NOW.minusSeconds(60));
        when(level.confirmationTime()).thenReturn(NOW.minusSeconds(30));
        when(level.swing()).thenReturn(null);
        return level;
    }

    private ConfirmedSwing swing(TrendContextRole role) {
        ConfirmedSwing swing = mock(ConfirmedSwing.class);
        when(swing.role()).thenReturn(role);
        when(swing.type()).thenReturn(SwingType.HIGH);
        when(swing.pivotTime()).thenReturn(NOW.minusSeconds(60));
        when(swing.confirmationTime()).thenReturn(NOW.minusSeconds(30));
        when(swing.pivotSourceId()).thenReturn(role.name() + "-pivot");
        when(swing.evidence()).thenReturn(new TrendContextEvidenceReference(
                role, role.name(), "rules", "rules-v1", "profile", "profile-v1", "input",
                NOW, List.of(role.name() + "-pivot"), NOW.minusSeconds(60), NOW.minusSeconds(30),
                role.name() + "-evidence"));
        return swing;
    }
}
