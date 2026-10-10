package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.application.port.RiskValidationAcknowledgmentRepository;
import com.hope.trading.market_intelligence.application.port.TradePlanRepository;
import com.hope.trading.market_intelligence.application.port.TradePlanRiskValidationBoundary;
import com.hope.trading.market_intelligence.application.port.TradePlanningContextRepository;
import com.hope.trading.market_intelligence.domain.opportunity.AiAnalysisReference;
import com.hope.trading.market_intelligence.domain.opportunity.ObservationReference;
import com.hope.trading.market_intelligence.domain.tradeplan.ExecutionParameters;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlan;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanId;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanStatus;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanVersion;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanningContext;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

public class TradePlanRiskHandoffService {
    private static final String CONTEXT_NOT_FOUND = "Referenced Trading Context snapshot not found";
    private final TradePlanRepository plans;
    private final TradePlanningContextRepository contexts;
    private final TradePlanRiskValidationBoundary lifecycle;
    private final RiskValidationAcknowledgmentRepository acknowledgments;
    private final Clock clock;
    private final Supplier<UUID> acknowledgmentIds;
    private final TradingOpportunityRepository opportunities;
    private final StrategyMatchRepository strategyMatches;

    public TradePlanRiskHandoffService(
            TradePlanRepository plans, TradePlanningContextRepository contexts,
            TradePlanRiskValidationBoundary lifecycle,
            RiskValidationAcknowledgmentRepository acknowledgments, Clock clock,
            Supplier<UUID> acknowledgmentIds,
            TradingOpportunityRepository opportunities,
            StrategyMatchRepository strategyMatches) {
        this.plans = plans;
        this.contexts = contexts;
        this.lifecycle = lifecycle;
        this.acknowledgments = acknowledgments;
        this.clock = clock;
        this.acknowledgmentIds = acknowledgmentIds;
        this.opportunities = opportunities;
        this.strategyMatches = strategyMatches;
    }

    public TradePlanRiskHandoffService(
            TradePlanRepository plans, TradePlanningContextRepository contexts,
            TradePlanRiskValidationBoundary lifecycle,
            RiskValidationAcknowledgmentRepository acknowledgments, Clock clock,
            Supplier<UUID> acknowledgmentIds) {
        this(plans, contexts, lifecycle, acknowledgments, clock, acknowledgmentIds, null, null);
    }

    public TradePlanRiskSnapshot loadAcceptedSnapshot(TradePlanId id, TradePlanVersion version) {
        TradePlan requested = plans.find(id, version).orElseThrow(() ->
                TradePlanRiskHandoffException.notFound("Trade Plan version not found"));
        TradePlan latest = plans.findLatest(id).orElseThrow(() ->
                TradePlanRiskHandoffException.notFound("Trade Plan not found"));
        boolean acknowledgedAcceptedVersion = requested.status() == TradePlanStatus.ACCEPTED
                && acknowledgments.find(id, version).isPresent()
                && (latest.status() == TradePlanStatus.RISK_VALIDATED
                || latest.status() == TradePlanStatus.READY_TO_EXECUTE);
        if (!latest.version().equals(version) && !acknowledgedAcceptedVersion) {
            throw TradePlanRiskHandoffException.conflict(
                    "STALE_TRADE_PLAN_VERSION", "Risk evaluation requires the latest Trade Plan version");
        }
        if (requested.status() != TradePlanStatus.ACCEPTED) {
            throw TradePlanRiskHandoffException.conflict(
                    "TRADE_PLAN_NOT_ACCEPTED", "Risk evaluation requires an ACCEPTED Trade Plan");
        }
        TradePlanningContext context = contexts.find(
                        requested.planningContext().id(), requested.planningContext().version())
                .orElseThrow(() -> TradePlanRiskHandoffException.notFound(
                        CONTEXT_NOT_FOUND));
        if (!context.capturedAt().equals(requested.planningContext().capturedAt())) {
            throw TradePlanRiskHandoffException.conflict(
                    "TRADING_CONTEXT_MISMATCH", "Referenced Trading Context identity is inconsistent");
        }
        if (!requested.execution().positionSizing().currency().equals(context.accountCurrency())) {
            throw TradePlanRiskHandoffException.conflict(
                    "POSITION_SIZING_CURRENCY_MISMATCH",
                    "Position sizing currency must equal the account currency");
        }
        return snapshot(requested, context);
    }

    @Transactional
    public TradePlanRiskSnapshot prepareForExecution(
            TradePlanId id, TradePlanVersion acceptedVersion, UUID evaluationId) {
        RiskValidationAcknowledgment acknowledgment = acknowledgments.find(id, acceptedVersion)
                .filter(item -> item.evaluationId().equals(evaluationId))
                .orElseThrow(() -> TradePlanRiskHandoffException.notFound(
                        "Risk validation acknowledgment not found"));
        TradePlan latest = plans.findLatestForUpdate(id).orElseThrow(() ->
                TradePlanRiskHandoffException.notFound("Trade Plan not found"));
        TradePlan ready = latest.status() == TradePlanStatus.READY_TO_EXECUTE
                ? latest
                : lifecycle.markReadyToExecute(id, new TradePlanVersion(
                        acknowledgment.riskValidatedTradePlanVersion()));
        TradePlanningContext context = contexts.find(
                        ready.planningContext().id(), ready.planningContext().version())
                .orElseThrow(() -> TradePlanRiskHandoffException.notFound(
                        CONTEXT_NOT_FOUND));
        return snapshot(ready, context);
    }

    public TradePlanRiskSnapshot loadReadySnapshot(TradePlanId id, TradePlanVersion version) {
        TradePlan ready = plans.find(id, version).orElseThrow(() ->
                TradePlanRiskHandoffException.notFound("Ready Trade Plan version not found"));
        if (ready.status() != TradePlanStatus.READY_TO_EXECUTE) {
            throw TradePlanRiskHandoffException.conflict(
                    "TRADE_PLAN_NOT_READY", "Execution requires a READY_TO_EXECUTE Trade Plan");
        }
        TradePlanningContext context = contexts.find(
                        ready.planningContext().id(), ready.planningContext().version())
                .orElseThrow(() -> TradePlanRiskHandoffException.notFound(
                        CONTEXT_NOT_FOUND));
        return snapshot(ready, context);
    }

    @Transactional
    public RiskValidationAcknowledgment acknowledgeApprovedEvaluation(
            TradePlanId id, TradePlanVersion acceptedVersion, UUID evaluationId,
            RiskValidationDecision decision, Instant evaluatedAt) {
        evaluatedAt = evaluatedAt.truncatedTo(ChronoUnit.MICROS);
        plans.findLatestForUpdate(id).orElseThrow(() ->
                TradePlanRiskHandoffException.notFound("Accepted Trade Plan version not found"));
        RiskValidationAcknowledgment prior = acknowledgments.find(id, acceptedVersion).orElse(null);
        if (prior != null) {
            if (prior.evaluationId().equals(evaluationId) && prior.decision() == decision
                    && prior.evaluatedAt().equals(evaluatedAt)) {
                return prior;
            }
            throw TradePlanRiskHandoffException.conflict(
                    "RISK_VALIDATION_ACKNOWLEDGMENT_CONFLICT",
                    "The accepted Trade Plan version is already linked to another evaluation");
        }
        if (decision != RiskValidationDecision.APPROVED
                && decision != RiskValidationDecision.APPROVED_WITH_WARNINGS) {
            throw TradePlanRiskHandoffException.invalidDecision(
                    "Only APPROVED or APPROVED_WITH_WARNINGS evaluations can be acknowledged");
        }
        RiskValidationAcknowledgment reused = acknowledgments.findByEvaluationId(evaluationId)
                .orElse(null);
        if (reused != null) {
            throw TradePlanRiskHandoffException.conflict(
                    "RISK_EVALUATION_ALREADY_LINKED",
                    "A changed Trade Plan version requires a new risk evaluation");
        }
        TradePlan accepted = plans.find(id, acceptedVersion).orElseThrow(() ->
                TradePlanRiskHandoffException.notFound("Accepted Trade Plan version not found"));
        TradePlan validated;
        try {
            validated = lifecycle.recordRiskValidated(id, acceptedVersion);
        } catch (IllegalStateException exception) {
            throw TradePlanRiskHandoffException.conflict(
                    accepted.status() == TradePlanStatus.ACCEPTED
                            ? "STALE_TRADE_PLAN_VERSION" : "TRADE_PLAN_NOT_ACCEPTED",
                    "Risk validation acknowledgment requires the exact latest ACCEPTED version");
        }
        return acknowledgments.save(new RiskValidationAcknowledgment(
                acknowledgmentIds.get(), id.value(), acceptedVersion.value(),
                validated.version().value(), accepted.planningContext().id(),
                accepted.planningContext().version(), evaluationId, decision, evaluatedAt,
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }

    private TradePlanRiskSnapshot snapshot(TradePlan plan, TradePlanningContext context) {
        ExecutionParameters execution = plan.execution();
        return new TradePlanRiskSnapshot(
                plan.id().value(), plan.version().value(), plan.status().name(), plan.origin().name(), plan.createdAt(),
                new TradePlanRiskSnapshot.Context(
                        context.id(), context.version(), context.capturedAt(), context.ownerId(),
                        context.tradingAccountId(), context.accountCurrency(),
                        context.riskBudget().sourceId(), context.riskBudget().sourceVersion(),
                        context.preferences().id(), context.preferences().version()),
                new TradePlanRiskSnapshot.Execution(
                        execution.instrument(), execution.direction().name(),
                        new TradePlanRiskSnapshot.Entry(
                                execution.entry().type().name(), execution.entry().price(),
                                execution.entry().conditions()),
                        execution.stopLoss() == null ? null : new TradePlanRiskSnapshot.StopLoss(
                                 execution.stopLoss().price(), execution.stopLoss().rationale()),
                        execution.takeProfits().stream().map(target ->
                                new TradePlanRiskSnapshot.TakeProfit(
                                        target.price(), target.allocationPercent())).toList(),
                        new TradePlanRiskSnapshot.PositionSizing(
                                execution.positionSizing().quantity(),
                                execution.positionSizing().notional(),
                                execution.positionSizing().expectedMonetaryRisk(),
                                execution.positionSizing().currency()),
                         execution.riskReward() == null ? null : execution.riskReward().ratio(),
                        new TradePlanRiskSnapshot.Expiration(
                                execution.expiration().expiresAt(),
                                execution.expiration().policy()),
                        execution.managementRules()),
                new TradePlanRiskSnapshot.Rationale(
                        plan.rationale().opportunities().stream()
                        .map(reference -> opportunity(reference.id().value(), reference.version().value()))
                                .collect(Collectors.toUnmodifiableSet()),
                        plan.rationale().observations().stream()
                                .map(ObservationReference::observationId)
                                .collect(Collectors.toUnmodifiableSet()),
                        plan.rationale().aiAnalyses().stream()
                                .map(AiAnalysisReference::analysisId)
                                .collect(Collectors.toUnmodifiableSet()),
                        plan.rationale().thesis(), plan.rationale().confirmationConditions(),
                        plan.rationale().invalidationConditions()));
    }

    private TradePlanRiskSnapshot.Opportunity opportunity(UUID id, long version) {
        if (opportunities == null) {
            return new TradePlanRiskSnapshot.Opportunity(
                    id, version, null, null, null, null, null, null, null, null);
        }
        return opportunities.find(new com.hope.trading.market_intelligence.domain.opportunity.OpportunityId(id),
                        new com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion(version))
                .map(value -> {
                    UUID matchId = value.strategyMatchId().orElse(null);
                    var match = matchId == null || strategyMatches == null
                            ? null : strategyMatches.findById(matchId).orElse(null);
                    return new TradePlanRiskSnapshot.Opportunity(id, version, matchId,
                            match == null ? null : match.strategyId().value(),
                            match == null ? null : match.strategyVersion(),
                            value.accountId().orElse(null), value.sourceScanId().orElse(null),
                            value.sourceScanMarketId().orElse(null), value.analysisExecutionId().orElse(null),
                            value.marketId().orElse(null));
                })
                .orElseGet(() -> new TradePlanRiskSnapshot.Opportunity(
                        id, version, null, null, null, null, null, null, null, null));
    }
}
