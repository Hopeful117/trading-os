package com.hope.trading.market_intelligence.domain.ai;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import com.hope.trading.market_intelligence.domain.opportunity.MarketThesisReference;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiAnalysisResultContractTest {
    @Test
    void acceptsAssessmentWithMatchingAuthorizedIdentity() {
        AiAnalysisIdentity identity = alignedIdentity(AiInterpretationMode.OPPORTUNITY_ASSESSMENT);
        AiAnalysisResult result = new AiAnalysisResult(identity,
                new OpportunityAssessmentResult(List.of("scenario remains coherent"), List.of(), List.of()),
                Instant.parse("2026-11-20T10:01:00Z"), null);

        ContextPack pack = contextPack(identity);
        AiAnalysisEligibility eligibility = new AiAnalysisEligibility(identity.opportunity(), true, true,
                true, List.of(), Set.of());

        assertThat(AiAnalysisResultValidator.requireMatches(identity, result, pack, eligibility))
                .isSameAs(result);
    }

    @Test
    void diagnosticRequiresDeterministicReason() {
        assertThatThrownBy(() -> new OpportunityDiagnosticResult(List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one diagnostic reason is required");
    }

    @Test
    void rejectsResultFromAnotherContextPack() {
        AiAnalysisIdentity expected = alignedIdentity(AiInterpretationMode.DIAGNOSTIC);
        AiAnalysisIdentity actual = new AiAnalysisIdentity(expected.executionId(), expected.idempotencyKey(), expected.mode(),
                new ContextPackId(UUID.randomUUID()), expected.contextPackSchemaVersion(),
                expected.contextPackFingerprint(), expected.marketId(), expected.opportunity(),
                expected.thesis());
        AiAnalysisResult result = new AiAnalysisResult(actual,
                new OpportunityDiagnosticResult(List.of(new DiagnosticReason(
                        "OPPORTUNITY_NOT_ACTIVE", "Opportunity is not active", List.of())),
                        List.of("not eligible for assessment"), List.of()),
                Instant.parse("2026-11-20T10:01:00Z"), null);

        ContextPack pack = contextPack(expected);
        AiAnalysisEligibility eligibility = new AiAnalysisEligibility(expected.opportunity(), true, true,
                false, List.of(new DiagnosticReason("OPPORTUNITY_NOT_ACTIVE", "Opportunity is not active", List.of())),
                Set.of());

        assertThatThrownBy(() -> AiAnalysisResultValidator.requireMatches(expected, result, pack, eligibility))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI analysis result does not match authorized identity");
    }

    @Test
    void rejectsDuplicateIdempotencyIdentity() {
        AiAnalysisIdentity identity = alignedIdentity(AiInterpretationMode.OPPORTUNITY_ASSESSMENT);
        AiAnalysisResult result = new AiAnalysisResult(identity,
                new OpportunityAssessmentResult(List.of("assessment"), List.of(), List.of()),
                Instant.parse("2026-11-20T10:01:00Z"), null);
        ContextPack pack = contextPack(identity);
        AiAnalysisEligibility eligibility = new AiAnalysisEligibility(identity.opportunity(), true, true,
                true, List.of(), Set.of());
        AiAnalysisResultLedger ledger = new AiAnalysisResultLedger();

        ledger.accept(identity, result, pack, eligibility);
        assertThatThrownBy(() -> ledger.accept(identity, result, pack, eligibility))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI analysis result already accepted for idempotency identity");
    }

    private ContextPack contextPack(AiAnalysisIdentity identity) {
        return ContextPack.from(new ContextPackId(identity.contextPackId().value()), identity.mode(),
                ContextPackAuthorization.publicAi(),
                new com.hope.trading.market_intelligence.domain.IntelligenceContext(
                        identity.marketId(), com.hope.trading.market_intelligence.domain.AnalysisExecutionMode.ACTIVE,
                        "assessment", Instant.parse("2026-11-20T10:00:00Z"), "analysis-context-v1",
                        com.hope.trading.market_intelligence.domain.ContextQuality.COMPLETE, List.of(),
                        ArtifactFingerprint.empty(), java.util.Map.of()), identity.opportunity(), identity.thesis());
    }

    private AiAnalysisIdentity identity(AiInterpretationMode mode) {
        return new AiAnalysisIdentity(UUID.randomUUID(), new IdempotencyKey("analysis-test"), mode,
                new ContextPackId(UUID.randomUUID()),
                "context-pack-v1", ArtifactFingerprint.empty(), UUID.randomUUID(),
                new OpportunityVersionReference(UUID.randomUUID(), 1),
                new MarketThesisReference(UUID.randomUUID(), 1));
    }

    private AiAnalysisIdentity alignedIdentity(AiInterpretationMode mode) {
        AiAnalysisIdentity base = identity(mode);
        ContextPack pack = contextPack(base);
        return new AiAnalysisIdentity(base.executionId(), base.idempotencyKey(), base.mode(),
                base.contextPackId(), base.contextPackSchemaVersion(), pack.fingerprint(),
                base.marketId(), base.opportunity(), base.thesis());
    }
}
