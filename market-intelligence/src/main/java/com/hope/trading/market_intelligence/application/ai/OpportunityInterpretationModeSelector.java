package com.hope.trading.market_intelligence.application.ai;

import com.hope.trading.market_intelligence.application.passive.PassiveCandidateEvaluator;
import com.hope.trading.market_intelligence.domain.ai.AiAnalysisEligibility;
import com.hope.trading.market_intelligence.domain.ai.AiInterpretationMode;
import com.hope.trading.market_intelligence.domain.ai.ContextPack;
import com.hope.trading.market_intelligence.domain.ai.DiagnosticReason;
import com.hope.trading.market_intelligence.domain.ai.OpportunityVersionReference;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public final class OpportunityInterpretationModeSelector {
    private static final Set<String> DIAGNOSABLE_CODES = Set.of(
            "OPPORTUNITY_NOT_ACTIVE",
            "OPPORTUNITY_OUTSIDE_VALIDITY",
            "THESIS_NOT_CANDIDATE",
            "THESIS_OUTSIDE_VALIDITY",
            "THESIS_BLOCKING_CONTRADICTION",
            "REQUIRED_EVIDENCE_NON_POSITIVE",
            "REQUIRED_EVIDENCE_EXPIRED",
            "REQUIRED_EVIDENCE_FRESHNESS_INVALID"
    );
    private static final Set<String> STRUCTURAL_CODES = Set.of(
            PassiveCandidateEvaluator.MARKET_IDENTITY_UNAVAILABLE,
            PassiveCandidateEvaluator.THESIS_SUPERSEDED,
            PassiveCandidateEvaluator.OPPORTUNITY_SUPERSEDED,
            "STRATEGY_MATCH_MISSING",
            "STRATEGY_MATCH_REFERENCE_UNAVAILABLE",
            "STRATEGY_MATCH_MARKET_MISMATCH",
            "STRATEGY_MATCH_DIRECTION_MISMATCH",
            "STRATEGY_MATCH_OBSERVATION_MISMATCH",
            "STRATEGY_MATCH_EXECUTION_MISMATCH",
            "STRATEGY_MATCH_FRESHNESS_INVALID",
            "OBSERVATION_REFERENCE_UNAVAILABLE",
            "MARKET_THESIS_REFERENCE_UNAVAILABLE",
            "MARKET_THESIS_OPPORTUNITY_MISMATCH",
            "MARKET_IDENTITY_MISSING",
            "MARKET_IDENTITY_MISMATCH",
            "PASSIVE_PROVENANCE_INCOMPLETE"
    );

    private final PassiveCandidateEvaluator evaluator;
    private final Clock clock;

    public OpportunityInterpretationModeSelector(PassiveCandidateEvaluator evaluator, Clock clock) {
        this.evaluator = evaluator;
        this.clock = clock;
    }

    public OpportunityInterpretationDecision select(
            OpportunityVersionReference opportunityReference,
            ContextPack contextPack
    ) {
        OpportunityInterpretationDecision decision = select(opportunityReference, contextPack.evaluationCutoff());
        return new OpportunityInterpretationDecision(decision.mode(), contextPack, decision.eligibility());
    }

    public OpportunityInterpretationDecision select(
            OpportunityVersionReference opportunityReference,
            Instant evaluationCutoff
    ) {
        var evaluation = evaluator.evaluateOne(
                new OpportunityId(opportunityReference.opportunityId()),
                new OpportunityVersion(opportunityReference.version()),
                evaluationCutoff);
        if (evaluation.isEmpty()) {
            throw new OpportunityInterpretationRejectedException("OPPORTUNITY_VERSION_NOT_FOUND");
        }

        var projection = evaluation.orElseThrow().projection();
        List<String> diagnostics = projection.diagnostics();
        if (diagnostics.contains(PassiveCandidateEvaluator.OPPORTUNITY_SUPERSEDED)) {
            throw new OpportunityInterpretationRejectedException("OPPORTUNITY_VERSION_SUPERSEDED");
        }
        if (diagnostics.stream().anyMatch(STRUCTURAL_CODES::contains)
                || projection.market() == null
                || projection.thesis() == null) {
            throw new OpportunityInterpretationRejectedException("OPPORTUNITY_REFERENCES_INCOHERENT");
        }

        boolean eligible = projection.current() && diagnostics.isEmpty();
        List<DiagnosticReason> reasons = diagnostics.stream()
                .filter(DIAGNOSABLE_CODES::contains)
                .map(code -> new DiagnosticReason(code, code, List.of()))
                .toList();
        if (!eligible && reasons.size() != diagnostics.size()) {
            throw new OpportunityInterpretationRejectedException("OPPORTUNITY_BLOCKER_NOT_DIAGNOSABLE");
        }

        AiAnalysisEligibility eligibility = new AiAnalysisEligibility(
                opportunityReference, true, true, eligible, reasons, Set.of());
        AiInterpretationMode mode = eligible
                ? AiInterpretationMode.OPPORTUNITY_ASSESSMENT
                : AiInterpretationMode.DIAGNOSTIC;
        return new OpportunityInterpretationDecision(mode, null, eligibility);
    }

    public Instant now() {
        return clock.instant();
    }
}
