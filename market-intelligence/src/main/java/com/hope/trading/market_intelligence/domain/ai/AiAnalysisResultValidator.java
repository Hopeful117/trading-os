package com.hope.trading.market_intelligence.domain.ai;

import java.util.Objects;

public final class AiAnalysisResultValidator {
    private AiAnalysisResultValidator() {
    }

    public static AiAnalysisResult requireMatches(
            AiAnalysisIdentity expected,
            AiAnalysisResult actual,
            ContextPack contextPack,
            AiAnalysisEligibility eligibility
    ) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(contextPack, "contextPack");
        Objects.requireNonNull(eligibility, "eligibility");
        if (!expected.equals(actual.identity())) {
            throw new IllegalArgumentException("AI analysis result does not match authorized identity");
        }
        if (!contextPack.id().equals(expected.contextPackId())
                || !contextPack.schemaVersion().equals(expected.contextPackSchemaVersion())
                || !contextPack.fingerprint().equals(expected.contextPackFingerprint())
                || !contextPack.mode().equals(expected.mode())
                || !contextPack.marketId().equals(expected.marketId())
                || !contextPack.opportunity().equals(expected.opportunity())) {
            throw new IllegalArgumentException("ContextPack does not match authorized identity");
        }
        if (!eligibility.opportunity().equals(expected.opportunity())
                || !eligibility.allows(expected.mode())) {
            throw new IllegalArgumentException("AI analysis mode is not deterministically eligible");
        }
        if (!contextPack.authorizedEvidenceReferences().containsAll(
                eligibility.authorizedEvidenceReferences())) {
            throw new IllegalArgumentException("Eligibility references unauthorized evidence");
        }
        validatePayload(actual.payload(), contextPack, eligibility);
        return actual;
    }

    private static void validatePayload(
            AiInterpretationPayload payload,
            ContextPack contextPack,
            AiAnalysisEligibility eligibility
    ) {
        if (payload instanceof OpportunityAssessmentResult assessment) {
            requireAuthorizedEvidence(assessment.evidenceReferences(), contextPack, eligibility);
            return;
        }
        OpportunityDiagnosticResult diagnostic = (OpportunityDiagnosticResult) payload;
        java.util.Set<String> allowedCodes = eligibility.deterministicReasons().stream()
                .map(DiagnosticReason::code).collect(java.util.stream.Collectors.toSet());
        if (!diagnostic.reasons().stream().allMatch(reason -> allowedCodes.contains(reason.code()))) {
            throw new IllegalArgumentException("Diagnostic contains an unauthorized reason");
        }
        if (!diagnostic.reasons().stream().allMatch(reason ->
                eligibility.authorizedEvidenceReferences().containsAll(reason.evidenceReferences()))) {
            throw new IllegalArgumentException("Diagnostic contains unauthorized reason evidence");
        }
        requireAuthorizedEvidence(diagnostic.evidenceReferences(), contextPack, eligibility);
    }

    private static void requireAuthorizedEvidence(
            java.util.Collection<java.util.UUID> references,
            ContextPack contextPack,
            AiAnalysisEligibility eligibility
    ) {
        if (!contextPack.authorizedEvidenceReferences().containsAll(references)
                || !eligibility.authorizedEvidenceReferences().containsAll(references)) {
            throw new IllegalArgumentException("Result contains unauthorized evidence");
        }
    }

    public static AiAnalysisResult requireMatches(
            AiAnalysisIdentity expected,
            AiAnalysisResult actual
    ) {
        throw new IllegalArgumentException(
                "Eligibility and ContextPack are required for AI result acceptance");
    }
}
