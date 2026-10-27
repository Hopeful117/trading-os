package com.hope.trading.market_intelligence.application.thesis;

import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.observation.TrendContextObservationPayload;
import com.hope.trading.market_intelligence.domain.observation.MarketThesisObservationPayload;
import com.hope.trading.market_intelligence.domain.momentum.MomentumAssessment;
import com.hope.trading.market_intelligence.domain.momentum.MomentumCapabilityContent;
import com.hope.trading.market_intelligence.domain.momentum.MomentumPolicy;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.trendcontext.*;
import com.hope.trading.market_intelligence.domain.tradeability.TradeabilityAssessment;
import com.hope.trading.market_intelligence.domain.tradeability.TradeabilityCapabilityContent;
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
        assertEquals(2, thesis.evidence().stream()
                .filter(value -> value.availability().name().equals("NOT_IMPLEMENTED"))
                .count());
        assertEquals(1, thesis.evidence().stream()
                .filter(value -> value.source().equals("MOMENTUM"))
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
    void directionalTrendWithoutMomentumConfirmationRemainsNoTrade() {
        Observation observation = missingObservation();
        TrendContextAssessment assessment = assessment(Map.of(
                TrendContextRole.BIAS, timeframe(TrendContextRole.BIAS),
                TrendContextRole.SETUP, timeframe(TrendContextRole.SETUP)));
        TrendContextObservationPayload payload = payload(assessment);
        when(observation.payload()).thenReturn(Optional.of(payload));

        var thesis = composer().compose(marketId, UUID.randomUUID(), observation);

        assertSame(MarketThesisState.NO_TRADE, thesis.state());
        assertEquals(3, thesis.scenario().orElseThrow().confirmationConditions().size());
        assertEquals(false, thesis.evidence().stream()
                .filter(value -> value.reference().equals("TRIGGER"))
                 .findFirst().orElseThrow().required());
    }

    @Test
    void neutralMomentumIsMissingUnknownEvidenceWithoutBreakingComposition() {
        Observation observation = missingObservation();
        TrendContextAssessment trend = assessment(Map.of(
                TrendContextRole.BIAS, timeframe(TrendContextRole.BIAS),
                TrendContextRole.SETUP, timeframe(TrendContextRole.SETUP)));
        MomentumAssessment momentum = new MomentumAssessment(
                marketId, "15m", NOW.minusSeconds(9000), NOW, NOW,
                MomentumAssessment.Status.AVAILABLE, MomentumAssessment.Direction.NEUTRAL,
                new java.math.BigDecimal("0.0"), new java.math.BigDecimal("0.0"),
                new java.math.BigDecimal("0.8"), new java.math.BigDecimal("0.8"),
                MomentumAssessment.Acceleration.STEADY, MomentumPolicy.V1.version(),
                MomentumAssessment.ALGORITHM_VERSION, ArtifactFingerprint.empty().value(),
                ArtifactFingerprint.ofInputs(List.of("neutral-momentum")).value(), "neutral");
        TrendContextCapabilityContent trendContent = mock(TrendContextCapabilityContent.class);
        when(trendContent.assessment()).thenReturn(trend);
        when(observation.payload()).thenReturn(Optional.of(new MarketThesisObservationPayload(
                trendContent, new MomentumCapabilityContent(momentum, "AVAILABLE", List.of(), NOW.plusSeconds(900)))));

        var thesis = composer().compose(marketId, UUID.randomUUID(), observation);

        assertThat(thesis.state()).isEqualTo(MarketThesisState.NO_TRADE);
        assertThat(thesis.evidence()).anyMatch(value ->
                value.reference().equals("MOMENTUM_NEUTRAL")
                        && value.role().name().equals("MISSING")
                        && value.availability().name().equals("UNKNOWN"));
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

        assertSame(MarketThesisState.NO_TRADE, thesis.state());
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

    @Test
    void deceleratingAlignedMomentumProducesWatchAndPendingConfirmation() {
        Observation observation = missingObservation();
        TrendContextAssessment trend = assessment(Map.of(
                TrendContextRole.BIAS, timeframe(TrendContextRole.BIAS),
                TrendContextRole.SETUP, timeframe(TrendContextRole.SETUP)));
        MomentumAssessment momentum = new MomentumAssessment(
                marketId, "15m", NOW.minusSeconds(9000), NOW, NOW,
                MomentumAssessment.Status.AVAILABLE, MomentumAssessment.Direction.UP,
                new java.math.BigDecimal("0.01"), new java.math.BigDecimal("1.0"),
                new java.math.BigDecimal("0.8"), new java.math.BigDecimal("0.2"),
                MomentumAssessment.Acceleration.DECELERATING, MomentumPolicy.V1.version(),
                MomentumAssessment.ALGORITHM_VERSION, ArtifactFingerprint.empty().value(),
                ArtifactFingerprint.ofInputs(List.of("momentum-result")).value(), "test");
        TrendContextCapabilityContent trendContent = mock(TrendContextCapabilityContent.class);
        when(trendContent.assessment()).thenReturn(trend);
        MarketThesisObservationPayload payload = new MarketThesisObservationPayload(
                trendContent, new MomentumCapabilityContent(momentum, "AVAILABLE", List.of(), NOW.plusSeconds(900)));
        when(observation.payload()).thenReturn(Optional.of(payload));

        var thesis = composer().compose(marketId, UUID.randomUUID(), observation);

        assertThat(thesis.state()).isEqualTo(MarketThesisState.WATCH);
        assertThat(thesis.reasons()).contains("MOMENTUM_WEAKENING");
        assertThat(thesis.scenario().orElseThrow().confirmationConditions())
                 .anyMatch(condition -> condition.code().equals("MOMENTUM_CONFIRMATION")
                         && condition.status().name().equals("PENDING"));
    }

    @Test
    void missingTradeabilityCannotProduceCandidate() {
        Observation observation = missingObservation();
        TrendContextAssessment trend = assessment(Map.of(
                TrendContextRole.BIAS, timeframe(TrendContextRole.BIAS),
                TrendContextRole.SETUP, timeframe(TrendContextRole.SETUP)));
        MomentumAssessment momentum = new MomentumAssessment(
                marketId, "15m", NOW.minusSeconds(9000), NOW, NOW,
                MomentumAssessment.Status.AVAILABLE, MomentumAssessment.Direction.UP,
                new java.math.BigDecimal("0.01"), new java.math.BigDecimal("1.0"),
                new java.math.BigDecimal("0.8"), new java.math.BigDecimal("0.8"),
                MomentumAssessment.Acceleration.ACCELERATING, MomentumPolicy.V1.version(),
                MomentumAssessment.ALGORITHM_VERSION, ArtifactFingerprint.empty().value(),
                ArtifactFingerprint.ofInputs(List.of("momentum-result")).value(), "test");
        TrendContextCapabilityContent trendContent = mock(TrendContextCapabilityContent.class);
        when(trendContent.assessment()).thenReturn(trend);
        when(observation.payload()).thenReturn(Optional.of(new MarketThesisObservationPayload(
                trendContent, new MomentumCapabilityContent(momentum, "AVAILABLE", List.of(), NOW.plusSeconds(900)))));

        var thesis = composer().compose(marketId, UUID.randomUUID(), observation);

        assertThat(thesis.state()).isEqualTo(MarketThesisState.NO_TRADE);
        assertThat(thesis.reasons()).contains("REQUIRED_EVIDENCE_UNAVAILABLE");
    }

    @Test
    void tradeabilityNewerThanAuthoritativeCutoffCannotComposeCurrentScenario() {
        Observation observation = missingObservation();
        TradeabilityAssessment assessment = new TradeabilityAssessment(
                marketId, "BTC/EUR", NOW, NOW, List.of(), "tradeability-v1", "tradeability-fingerprint");
        when(observation.payload()).thenReturn(Optional.of(new MarketThesisObservationPayload(
                null, null, new TradeabilityCapabilityContent(
                        assessment, "AVAILABLE", List.of(), NOW.plusSeconds(900)))));

        var thesis = composer().compose(
                marketId, UUID.randomUUID(), observation, NOW.minusSeconds(60));

        assertThat(thesis.evidence()).anyMatch(value ->
                value.reference().equals("TRADEABILITY_CUTOFF_INCOMPATIBLE"));
    }

    @Test
    void tradeabilityFromAnotherMarketCannotComposeCurrentScenario() {
        Observation observation = missingObservation();
        TradeabilityAssessment assessment = new TradeabilityAssessment(
                UUID.randomUUID(), "BTC/EUR", NOW, NOW, List.of(), "tradeability-v1", "tradeability-fingerprint");
        when(observation.payload()).thenReturn(Optional.of(new MarketThesisObservationPayload(
                null, null, new TradeabilityCapabilityContent(
                        assessment, "AVAILABLE", List.of(), NOW.plusSeconds(900)))));

        var thesis = composer().compose(marketId, UUID.randomUUID(), observation);

        assertThat(thesis.evidence()).anyMatch(value ->
                value.reference().equals("TRADEABILITY_IDENTITY_INCOMPATIBLE"));
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
