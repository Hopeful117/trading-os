package com.hope.trading.market_intelligence.application.thesis;

import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.observation.TrendContextObservationPayload;
import com.hope.trading.market_intelligence.domain.trendcontext.*;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisState;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class MarketThesisComposerTest {
    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");
    private final UUID marketId = UUID.randomUUID();
    private final UUID observationId = UUID.randomUUID();

    @Test
    void missingTrendContextProducesNoTradeAndExplicitMissingCapabilities() {
        Observation observation = mock(Observation.class);
        when(observation.id()).thenReturn(observationId);
        when(observation.version()).thenReturn(1L);
        when(observation.instrument()).thenReturn("BTC/EUR");
        when(observation.evidence()).thenReturn(List.of());
        when(observation.validFrom()).thenReturn(NOW);
        when(observation.validUntil()).thenReturn(Optional.of(NOW.plusSeconds(900)));
        when(observation.payload()).thenReturn(Optional.empty());

        MarketThesisComposer composer = composer();
        var thesis = composer.compose(marketId, UUID.randomUUID(), observation);

        assertEquals(com.hope.trading.market_intelligence.domain.thesis.MarketThesisState.NO_TRADE,
                thesis.state());
        assertEquals(3, thesis.evidence().stream()
                .filter(value -> value.availability().name().equals("NOT_IMPLEMENTED"))
                .count());
    }

    @Test
    void identicalInputsProduceIdenticalThesisContent() {
        Observation observation = missingObservation();
        MarketThesisComposer composer = composer();

        UUID analysisExecutionId = UUID.randomUUID();
        var first = composer.compose(marketId, analysisExecutionId, observation);
        var second = composer.compose(marketId, analysisExecutionId, observation);

        assertEquals(first.id(), second.id());
        assertEquals(first.lineageId(), second.lineageId());
        assertEquals(first.inputFingerprint(), second.inputFingerprint());
        assertEquals(first.evidence(), second.evidence());
        assertEquals(first.reasons(), second.reasons());
    }

    @Test
    void directionalTrendWithoutBlockingEvidenceProducesCandidate() {
        Observation observation = missingObservation();
        TrendContextAssessment assessment = assessment(Map.of(
                TrendContextRole.BIAS, timeframe(TrendContextRole.BIAS),
                TrendContextRole.SETUP, timeframe(TrendContextRole.SETUP)));
        TrendContextObservationPayload payload = payload(assessment);
        when(observation.payload()).thenReturn(Optional.of(payload));

        var thesis = composer().compose(marketId, UUID.randomUUID(), observation);

        assertSame(MarketThesisState.CANDIDATE, thesis.state());
        assertEquals(1, thesis.scenario().orElseThrow().confirmationConditions().size());
        assertEquals(false, thesis.evidence().stream()
                .filter(value -> value.reference().equals("TRIGGER"))
                .findFirst().orElseThrow().required());
    }

    @Test
    void missingRequiredRoleProducesNoTrade() {
        Observation observation = missingObservation();
        TrendContextAssessment assessment = assessment(Map.of(
                TrendContextRole.BIAS, timeframe(TrendContextRole.BIAS)));
        TrendContextObservationPayload payload = payload(assessment);
        when(observation.payload()).thenReturn(Optional.of(payload));

        var thesis = composer().compose(marketId, UUID.randomUUID(), observation);

        assertSame(MarketThesisState.NO_TRADE, thesis.state());
        assertEquals(true, thesis.evidence().stream()
                .filter(value -> value.reference().equals("SETUP"))
                .findFirst().orElseThrow().required());
    }

    @Test
    void staleRequiredRoleProducesWatch() {
        Observation observation = missingObservation();
        TrendContextTimeframeAssessment staleSetup = timeframe(TrendContextRole.SETUP);
        when(staleSetup.fresh()).thenReturn(false);
        TrendContextAssessment assessment = assessment(Map.of(
                TrendContextRole.BIAS, timeframe(TrendContextRole.BIAS),
                TrendContextRole.SETUP, staleSetup));
        TrendContextObservationPayload payload = payload(assessment);
        when(observation.payload()).thenReturn(Optional.of(payload));

        var thesis = composer().compose(marketId, UUID.randomUUID(), observation);

        assertSame(MarketThesisState.WATCH, thesis.state());
    }

    @Test
    void assessmentNewerThanAuthoritativeCutoffCannotComposeCurrentScenario() {
        Observation observation = missingObservation();
        TrendContextAssessment assessment = assessment(Map.of(
                TrendContextRole.BIAS, timeframe(TrendContextRole.BIAS),
                TrendContextRole.SETUP, timeframe(TrendContextRole.SETUP)));
        TrendContextObservationPayload payload = payload(assessment);
        when(observation.payload()).thenReturn(Optional.of(payload));

        var thesis = composer().compose(
                marketId, UUID.randomUUID(), observation, NOW.minusSeconds(60));

        assertThat(thesis.scenario()).isEmpty();
        assertThat(thesis.evidence()).anyMatch(value ->
                value.reference().equals("TREND_CONTEXT_CUTOFF_INCOMPATIBLE"));
    }

    private Observation missingObservation() {
        Observation observation = mock(Observation.class);
        when(observation.id()).thenReturn(observationId);
        when(observation.lineageId()).thenReturn(observationId);
        when(observation.version()).thenReturn(1L);
        when(observation.instrument()).thenReturn("BTC/EUR");
        when(observation.evidence()).thenReturn(List.of());
        when(observation.createdAt()).thenReturn(NOW);
        when(observation.validFrom()).thenReturn(NOW);
        when(observation.validUntil()).thenReturn(Optional.of(NOW.plusSeconds(900)));
        when(observation.supersedes()).thenReturn(Optional.empty());
        when(observation.payload()).thenReturn(Optional.empty());
        when(observation.horizon()).thenReturn("15m");
        return observation;
    }

    private MarketThesisComposer composer() {
        return new MarketThesisComposer(Clock.fixed(NOW, ZoneOffset.UTC), profile());
    }

    private TrendContextProfile profile() {
        return TrendContextProfile.conservativeSwingV1(Map.of(
                TrendContextRole.BIAS, definition(TrendContextRole.BIAS, true),
                TrendContextRole.SETUP, definition(TrendContextRole.SETUP, true),
                TrendContextRole.TRIGGER, definition(TrendContextRole.TRIGGER, false)));
    }

    private TrendContextRoleDefinition definition(TrendContextRole role, boolean required) {
        String interval = switch (role) {
            case BIAS -> "FOUR_HOURS";
            case SETUP -> "ONE_HOUR";
            case TRIGGER -> "FIFTEEN_MINUTES";
        };
        Duration duration = switch (role) {
            case BIAS -> Duration.ofHours(4);
            case SETUP -> Duration.ofHours(1);
            case TRIGGER -> Duration.ofMinutes(15);
        };
        return new TrendContextRoleDefinition(role, interval, duration, required, 1, 1);
    }

    private TrendContextTimeframeAssessment timeframe(TrendContextRole role) {
        TrendContextTimeframeAssessment value = mock(TrendContextTimeframeAssessment.class);
        when(value.role()).thenReturn(role);
        when(value.interval()).thenReturn(role.name());
        when(value.fresh()).thenReturn(true);
        when(value.levels()).thenReturn(List.of());
        return value;
    }

    private TrendContextAssessment assessment(Map<TrendContextRole, TrendContextTimeframeAssessment> timeframes) {
        TrendContextAssessment assessment = mock(TrendContextAssessment.class);
        when(assessment.marketId()).thenReturn(marketId);
        when(assessment.direction()).thenReturn(TrendDirection.UP);
        when(assessment.assessmentAt()).thenReturn(NOW);
        when(assessment.cutOffAt()).thenReturn(NOW);
        when(assessment.fingerprint()).thenReturn("assessment-fingerprint");
        when(assessment.profileVersion()).thenReturn("profile-v1");
        when(assessment.ruleVersion()).thenReturn("rules-v1");
        when(assessment.provider()).thenReturn("provider");
        when(assessment.symbol()).thenReturn("BTC/EUR");
        when(assessment.findings()).thenReturn(List.of());
        when(assessment.contradictions()).thenReturn(List.of());
        when(assessment.exclusions()).thenReturn(List.of());
        when(assessment.invalidations()).thenReturn(List.of());
        when(assessment.timeframes()).thenReturn(timeframes);
        return assessment;
    }

    private TrendContextObservationPayload payload(TrendContextAssessment assessment) {
        TrendContextCapabilityContent content = mock(TrendContextCapabilityContent.class);
        when(content.assessment()).thenReturn(assessment);
        return new TrendContextObservationPayload(content);
    }
}
