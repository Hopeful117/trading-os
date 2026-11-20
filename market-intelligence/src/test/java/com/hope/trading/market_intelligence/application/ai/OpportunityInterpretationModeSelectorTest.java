package com.hope.trading.market_intelligence.application.ai;

import com.hope.trading.market_intelligence.application.passive.PassiveAwarenessProjection;
import com.hope.trading.market_intelligence.application.passive.PassiveCandidateEvaluator;
import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.ai.*;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.opportunity.MarketThesisReference;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OpportunityInterpretationModeSelectorTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-20T10:00:00Z");
    private final PassiveCandidateEvaluator evaluator = mock(PassiveCandidateEvaluator.class);
    private final OpportunityInterpretationModeSelector selector = new OpportunityInterpretationModeSelector(
            evaluator, Clock.fixed(CUTOFF, ZoneOffset.UTC));

    @Test
    void selectsAssessmentForEligibleExactVersion() {
        OpportunityVersionReference reference = reference();
        when(evaluator.evaluateOne(any(), any(), eq(CUTOFF))).thenReturn(java.util.Optional.of(
                new PassiveCandidateEvaluator.SingleEvaluation(projection(List.of(), true), false)));

        OpportunityInterpretationDecision decision = selector.select(reference, pack(AiInterpretationMode.OPPORTUNITY_ASSESSMENT));

        assertThat(decision.mode()).isEqualTo(AiInterpretationMode.OPPORTUNITY_ASSESSMENT);
        assertThat(decision.eligibility().allows(AiInterpretationMode.OPPORTUNITY_ASSESSMENT)).isTrue();
        verify(evaluator).evaluateOne(any(), any(), eq(CUTOFF));
    }

    @Test
    void selectsDiagnosticOnlyForAllowListedDeterministicBlocker() {
        OpportunityVersionReference reference = reference();
        when(evaluator.evaluateOne(any(), any(), eq(CUTOFF))).thenReturn(java.util.Optional.of(
                new PassiveCandidateEvaluator.SingleEvaluation(
                        projection(List.of("OPPORTUNITY_OUTSIDE_VALIDITY"), false), false)));

        OpportunityInterpretationDecision decision = selector.select(reference, pack(AiInterpretationMode.DIAGNOSTIC));

        assertThat(decision.mode()).isEqualTo(AiInterpretationMode.DIAGNOSTIC);
        assertThat(decision.eligibility().deterministicReasons())
                .extracting(DiagnosticReason::code)
                .containsExactly("OPPORTUNITY_OUTSIDE_VALIDITY");
        verify(evaluator).evaluateOne(any(), any(), eq(CUTOFF));
    }

    @Test
    void rejectsStructuralBlockersBeforeDiagnostic() {
        OpportunityVersionReference reference = reference();
        when(evaluator.evaluateOne(any(), any(), eq(CUTOFF))).thenReturn(java.util.Optional.of(
                new PassiveCandidateEvaluator.SingleEvaluation(
                        projection(List.of("MARKET_IDENTITY_MISMATCH"), false), false)));

        assertThatThrownBy(() -> selector.select(reference, pack(AiInterpretationMode.DIAGNOSTIC)))
                .isInstanceOf(OpportunityInterpretationRejectedException.class)
                .hasMessage("OPPORTUNITY_REFERENCES_INCOHERENT");
    }

    @Test
    void rejectsMissingExactVersion() {
        when(evaluator.evaluateOne(any(), any(), eq(CUTOFF))).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> selector.select(reference(), pack(AiInterpretationMode.OPPORTUNITY_ASSESSMENT)))
                .isInstanceOf(OpportunityInterpretationRejectedException.class)
                .hasMessage("OPPORTUNITY_VERSION_NOT_FOUND");
    }

    private PassiveAwarenessProjection projection(List<String> diagnostics, boolean current) {
        return new PassiveAwarenessProjection(mock(com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity.class),
                mock(com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse.class),
                mock(com.hope.trading.market_intelligence.domain.thesis.MarketThesis.class),
                List.of(), diagnostics, current);
    }

    private ContextPack pack(AiInterpretationMode mode) {
        UUID marketId = UUID.randomUUID();
        IntelligenceContext context = new IntelligenceContext(
                marketId, AnalysisExecutionMode.PASSIVE, "OPPORTUNITY_INTERPRETATION", CUTOFF,
                "analysis-context-v1", ContextQuality.COMPLETE, List.of(), ArtifactFingerprint.empty(),
                Map.of(ContextSectionType.MARKET_IDENTITY, new ContextSection(
                        ContextSectionType.MARKET_IDENTITY, ContextSectionStatus.AVAILABLE,
                        ContextSensitivity.PUBLIC, null, null, null)));
        return ContextPack.from(new ContextPackId(UUID.randomUUID()), mode, ContextPackAuthorization.publicAi(),
                context, reference(), new MarketThesisReference(UUID.randomUUID(), 1));
    }

    private OpportunityVersionReference reference() {
        return new OpportunityVersionReference(UUID.fromString("00000000-0000-0000-0000-000000000001"), 1);
    }
}
