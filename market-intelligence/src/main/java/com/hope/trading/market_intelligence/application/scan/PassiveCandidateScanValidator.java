package com.hope.trading.market_intelligence.application.scan;

import com.hope.trading.market_intelligence.application.passive.PassiveAwarenessProjection;
import com.hope.trading.market_intelligence.application.passive.PassiveCandidateEvaluator;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService;
import com.hope.trading.market_intelligence.application.strategy.ActiveAnalysisStrategy;
import com.hope.trading.market_intelligence.application.strategy.PassiveAnalysisStrategy;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionProvenance;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityOrigin;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import com.hope.trading.market_intelligence.domain.scan.PassiveCandidateProvenance;
import com.hope.trading.market_intelligence.domain.scan.PassiveCandidateReference;
import com.hope.trading.market_intelligence.domain.scan.PassiveEvidenceAssessment;
import com.hope.trading.market_intelligence.domain.scan.PassiveEvidenceAssessment.Dimension;
import com.hope.trading.market_intelligence.domain.scan.PassiveEvidenceAssessment.Status;
import com.hope.trading.market_intelligence.domain.scope.MarketScopeMode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Story 0243: server-side revalidation of a client-selected passive candidate before an
 * ActiveScan is created. Grants no account eligibility; it only proves the candidate is
 * current, consistent with the requested scope, and captures immutable provenance.
 * Passive evidence is assessed and referenced, never substituted for active inputs (D1).
 */
@Component
public class PassiveCandidateScanValidator {
    private final PassiveCandidateEvaluator evaluator;
    private final AnalysisExecutionRepository executions;
    private final PassiveAnalysisStrategy passiveStrategy;
    private final ActiveAnalysisStrategy activeStrategy;

    public PassiveCandidateScanValidator(
            PassiveCandidateEvaluator evaluator,
            AnalysisExecutionRepository executions,
            PassiveAnalysisStrategy passiveStrategy,
            ActiveAnalysisStrategy activeStrategy) {
        this.evaluator = Objects.requireNonNull(evaluator);
        this.executions = Objects.requireNonNull(executions);
        this.passiveStrategy = Objects.requireNonNull(passiveStrategy);
        this.activeStrategy = Objects.requireNonNull(activeStrategy);
    }

    public PassiveCandidateProvenance validate(
            PassiveCandidateReference reference, CreateActiveScanCommand command, Instant now) {
        PassiveCandidateEvaluator.SingleEvaluation evaluation = evaluator.evaluateOne(
                        new OpportunityId(reference.opportunityId()),
                        new OpportunityVersion(reference.opportunityVersion()), now)
                .orElseThrow(() -> new ActiveScanException(
                        "PASSIVE_CANDIDATE_NOT_FOUND", "Passive candidate version not found", 404));
        PassiveAwarenessProjection projection = evaluation.projection();
        TradingOpportunity opportunity = projection.opportunity();

        if (opportunity.origin() != OpportunityOrigin.PASSIVE_SCAN || opportunity.accountId().isPresent()) {
            throw new ActiveScanException("PASSIVE_CANDIDATE_INVALID_ORIGIN",
                    "Referenced opportunity is not an accountless passive candidate", 422);
        }
        UUID marketId = opportunity.marketId().orElse(null);
        if (marketId == null || !scopeMatches(command, marketId)) {
            throw new ActiveScanException("PASSIVE_CANDIDATE_MARKET_MISMATCH",
                    "A passive candidate scan requires SELECTED scope containing exactly the candidate market",
                    422);
        }
        List<String> diagnostics = projection.diagnostics();
        if (diagnostics.contains(PassiveCandidateEvaluator.OPPORTUNITY_SUPERSEDED)
                || diagnostics.contains(PassiveCandidateEvaluator.THESIS_SUPERSEDED)) {
            throw new ActiveScanException("PASSIVE_CANDIDATE_SUPERSEDED",
                    "Passive candidate has been superseded: " + String.join(",", diagnostics), 409);
        }
        if (evaluation.marketDataUnavailable()) {
            throw new ActiveScanException("PASSIVE_CANDIDATE_IDENTITY_UNAVAILABLE",
                    "Market identity is unavailable for passive candidate validation", 503);
        }
        if (!projection.current()) {
            throw new ActiveScanException("PASSIVE_CANDIDATE_NOT_CURRENT",
                    "Passive candidate is no longer current: " + String.join(",", diagnostics), 409);
        }
        return provenance(projection, command, now);
    }

    private boolean scopeMatches(CreateActiveScanCommand command, UUID marketId) {
        if (command.scopeMode() != MarketScopeMode.SELECTED || command.requestedMarketIds() == null) {
            return false;
        }
        List<UUID> requested = command.requestedMarketIds().stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .stream().toList();
        return requested.equals(List.of(marketId));
    }

    private PassiveCandidateProvenance provenance(
            PassiveAwarenessProjection projection, CreateActiveScanCommand command, Instant now) {
        TradingOpportunity opportunity = projection.opportunity();
        var thesis = projection.thesis();
        var market = projection.market();
        return new PassiveCandidateProvenance(
                opportunity.id().value(),
                opportunity.version().value(),
                opportunity.strategyMatchId().orElse(null),
                opportunity.observations().stream().map(value -> value.observationId()).sorted().toList(),
                thesis.id(),
                thesis.version(),
                opportunity.sourceScanId().orElse(null),
                opportunity.sourceScanMarketId().orElse(null),
                opportunity.analysisExecutionId().orElse(null),
                opportunity.marketId().orElseThrow(),
                market.provider(),
                market.productType(),
                opportunity.evaluatedAt(),
                thesis.sourceCutoff(),
                opportunity.validUntil().orElse(null),
                now,
                assess(projection, command, now));
    }

    private List<PassiveEvidenceAssessment> assess(
            PassiveAwarenessProjection projection, CreateActiveScanCommand command, Instant now) {
        TradingOpportunity opportunity = projection.opportunity();
        Optional<AnalysisExecutionProvenance> passive = opportunity.analysisExecutionId()
                .flatMap(executions::findById)
                .map(execution -> execution.provenance());
        Duration bound = ActiveScanScopeResolutionService.CURRENT_MARKET_EVIDENCE_MAX_AGE;

        PassiveEvidenceAssessment policy = passive
                .map(value -> value.evidencePolicy() == EvidenceAcquisitionPolicy.LIVE_CURRENT
                        ? compatible(Dimension.EVIDENCE_POLICY, "LIVE_CURRENT")
                        : reacquired(Dimension.EVIDENCE_POLICY, "PASSIVE_POLICY_" + value.evidencePolicy()))
                .orElse(reacquired(Dimension.EVIDENCE_POLICY, "PASSIVE_EXECUTION_UNAVAILABLE"));

        PassiveEvidenceAssessment capabilities = passive
                .map(value -> {
                    UUID marketId = opportunity.marketId().orElseThrow();
                    List<String> passivePlan = passiveStrategy.plan(new IntelligenceAnalysisRequest(
                            UUID.randomUUID(), marketId, AnalysisExecutionMode.PASSIVE, "PASSIVE_SCAN",
                            null, null, null, value.evaluationCutoff(), value.evidencePolicy(), null, null))
                            .capabilityIds();
                    List<String> activePlan = activeStrategy.plan(new IntelligenceAnalysisRequest(
                            UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE, "ACTIVE_SCAN",
                            null, null, null, now, EvidenceAcquisitionPolicy.LIVE_CURRENT, null, null)
                            .withTradeabilityEvidenceProfile(command.tradeabilityEvidenceProfile()))
                            .capabilityIds();
                    List<String> missing = activePlan.stream()
                            .filter(id -> !new HashSet<>(passivePlan).contains(id)).sorted().toList();
                    return missing.isEmpty()
                            ? compatible(Dimension.CAPABILITIES, "PASSIVE_PLAN_COVERS_ACTIVE_PLAN")
                            : reacquired(Dimension.CAPABILITIES, "MISSING:" + String.join(",", missing));
                })
                .orElse(reacquired(Dimension.CAPABILITIES, "PASSIVE_EXECUTION_UNAVAILABLE"));

        PassiveEvidenceAssessment cutoff = passive
                .map(value -> value.evaluationCutoff() == null
                        ? reacquired(Dimension.CUTOFF, "PASSIVE_CUTOFF_UNDECLARED")
                        : withinBound(value.evaluationCutoff(), now, bound)
                        ? compatible(Dimension.CUTOFF, "WITHIN_" + bound)
                        : reacquired(Dimension.CUTOFF, "OLDER_THAN_" + bound))
                .orElse(reacquired(Dimension.CUTOFF, "PASSIVE_EXECUTION_UNAVAILABLE"));

        Instant sourceCutoff = projection.thesis().sourceCutoff();
        PassiveEvidenceAssessment freshness = withinBound(sourceCutoff, now, bound)
                ? compatible(Dimension.FRESHNESS, "WITHIN_" + bound)
                : reacquired(Dimension.FRESHNESS, "OLDER_THAN_" + bound);

        return List.of(policy, capabilities, cutoff, freshness);
    }

    private static boolean withinBound(Instant at, Instant now, Duration bound) {
        return !at.isAfter(now) && !at.isBefore(now.minus(bound));
    }

    private static PassiveEvidenceAssessment compatible(Dimension dimension, String reason) {
        return new PassiveEvidenceAssessment(dimension, Status.COMPATIBLE_REFERENCE_ONLY, reason);
    }

    private static PassiveEvidenceAssessment reacquired(Dimension dimension, String reason) {
        return new PassiveEvidenceAssessment(dimension, Status.INCOMPATIBLE_REACQUIRED, reason);
    }
}
