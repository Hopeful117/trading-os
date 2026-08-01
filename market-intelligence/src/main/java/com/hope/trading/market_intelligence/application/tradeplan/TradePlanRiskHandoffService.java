package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.application.port.RiskValidationAcknowledgmentRepository;
import com.hope.trading.market_intelligence.application.port.TradePlanRepository;
import com.hope.trading.market_intelligence.application.port.TradePlanRiskValidationBoundary;
import com.hope.trading.market_intelligence.application.port.TradingContextRepository;
import com.hope.trading.market_intelligence.domain.opportunity.AiAnalysisReference;
import com.hope.trading.market_intelligence.domain.opportunity.ObservationReference;
import com.hope.trading.market_intelligence.domain.tradeplan.ExecutionParameters;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlan;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanId;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanStatus;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanVersion;
import com.hope.trading.market_intelligence.domain.tradeplan.TradingContext;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

public class TradePlanRiskHandoffService {
    private final TradePlanRepository plans;
    private final TradingContextRepository contexts;
    private final TradePlanRiskValidationBoundary lifecycle;
    private final RiskValidationAcknowledgmentRepository acknowledgments;
    private final Clock clock;
    private final Supplier<UUID> acknowledgmentIds;

    public TradePlanRiskHandoffService(
            TradePlanRepository plans, TradingContextRepository contexts,
            TradePlanRiskValidationBoundary lifecycle,
            RiskValidationAcknowledgmentRepository acknowledgments, Clock clock,
            Supplier<UUID> acknowledgmentIds) {
        this.plans = plans;
        this.contexts = contexts;
        this.lifecycle = lifecycle;
        this.acknowledgments = acknowledgments;
        this.clock = clock;
        this.acknowledgmentIds = acknowledgmentIds;
    }

    public TradePlanRiskSnapshot loadAcceptedSnapshot(TradePlanId id, TradePlanVersion version) {
        TradePlan requested = plans.find(id, version).orElseThrow(() ->
                TradePlanRiskHandoffException.notFound("Trade Plan version not found"));
        TradePlan latest = plans.findLatest(id).orElseThrow(() ->
                TradePlanRiskHandoffException.notFound("Trade Plan not found"));
        if (!latest.version().equals(version)) {
            throw TradePlanRiskHandoffException.conflict(
                    "STALE_TRADE_PLAN_VERSION", "Risk evaluation requires the latest Trade Plan version");
        }
        if (requested.status() != TradePlanStatus.ACCEPTED) {
            throw TradePlanRiskHandoffException.conflict(
                    "TRADE_PLAN_NOT_ACCEPTED", "Risk evaluation requires an ACCEPTED Trade Plan");
        }
        TradingContext context = contexts.find(
                        requested.tradingContext().id(), requested.tradingContext().version())
                .orElseThrow(() -> TradePlanRiskHandoffException.notFound(
                        "Referenced Trading Context snapshot not found"));
        if (!context.snapshotAt().equals(requested.tradingContext().snapshotAt())) {
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
                validated.version().value(), accepted.tradingContext().id(),
                accepted.tradingContext().version(), evaluationId, decision, evaluatedAt,
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }

    private TradePlanRiskSnapshot snapshot(TradePlan plan, TradingContext context) {
        ExecutionParameters execution = plan.execution();
        return new TradePlanRiskSnapshot(
                plan.id().value(), plan.version().value(), plan.status().name(), plan.createdAt(),
                new TradePlanRiskSnapshot.Context(
                        context.id(), context.version(), context.snapshotAt(), context.ownerId(),
                        context.tradingAccountId(), context.accountCurrency(),
                        context.availableCapital(), context.buyingPower(), context.leverage(),
                        context.riskProfile(), context.ruleProfile(), context.existingExposure(),
                        context.executionPreferences()),
                new TradePlanRiskSnapshot.Execution(
                        execution.instrument(), execution.direction().name(),
                        new TradePlanRiskSnapshot.Entry(
                                execution.entry().type().name(), execution.entry().price(),
                                execution.entry().conditions()),
                        new TradePlanRiskSnapshot.StopLoss(
                                execution.stopLoss().price(), execution.stopLoss().rationale()),
                        execution.takeProfits().stream().map(target ->
                                new TradePlanRiskSnapshot.TakeProfit(
                                        target.price(), target.allocationPercent())).toList(),
                        new TradePlanRiskSnapshot.PositionSizing(
                                execution.positionSizing().quantity(),
                                execution.positionSizing().notional(),
                                execution.positionSizing().expectedMonetaryRisk(),
                                execution.positionSizing().currency()),
                        execution.riskReward().ratio(),
                        new TradePlanRiskSnapshot.Expiration(
                                execution.expiration().expiresAt(),
                                execution.expiration().policy()),
                        execution.managementRules()),
                new TradePlanRiskSnapshot.Rationale(
                        plan.rationale().opportunities().stream()
                                .map(reference -> new TradePlanRiskSnapshot.Opportunity(
                                        reference.id().value(), reference.version().value()))
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
}
