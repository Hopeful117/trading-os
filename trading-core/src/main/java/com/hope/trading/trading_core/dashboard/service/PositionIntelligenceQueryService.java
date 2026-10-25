package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.challenge.application.ChallengeNotStartedException;
import com.hope.trading.trading_core.challenge.application.ChallengeProgress;
import com.hope.trading.trading_core.challenge.application.ChallengeProgressService;
import com.hope.trading.trading_core.dashboard.model.OpenPositionDashboardView;
import com.hope.trading.trading_core.dashboard.model.PositionIntelligenceView;
import com.hope.trading.trading_core.dashboard.model.PositionSource;
import com.hope.trading.trading_core.dashboard.model.PositionValuationStatus;
import com.hope.trading.trading_core.execution.domain.aggregate.TradeOutcome;
import com.hope.trading.trading_core.execution.domain.repository.TradeOutcomeRepositoryPort;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.risk.application.TradeScenarioProjectionResponse;
import com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient;
import com.hope.trading.risk.projection.TradeScenarioProjectionEngine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PositionIntelligenceQueryService {
    private final TradeOutcomeRepositoryPort outcomes;
    private final MarketIntelligenceTradePlanningClient tradePlans;
    private final ChallengeProgressService challenges;
    private final Clock clock;
    private final TradeScenarioProjectionEngine scenarioEngine = new TradeScenarioProjectionEngine();

    public PositionIntelligenceQueryService(TradeOutcomeRepositoryPort outcomes,
                                            MarketIntelligenceTradePlanningClient tradePlans,
                                            ChallengeProgressService challenges,
                                            Clock clock) {
        this.outcomes = outcomes;
        this.tradePlans = tradePlans;
        this.challenges = challenges;
        this.clock = clock;
    }

    public PositionIntelligenceView query(UUID actorId, Account account, OpenPositionDashboardView position,
                                          BigDecimal balance, BigDecimal equity) {
        if (!account.getAccountId().equals(position.accountId())) {
            throw new IllegalArgumentException("Position does not belong to the account");
        }

        LineageResult lineage = resolveLineage(actorId, account, position);
        PositionIntelligenceView.Rationale rationale = lineage.plan() == null
                ? new PositionIntelligenceView.Rationale("UNAVAILABLE", null, Set.of(), Set.of(), Set.of(),
                lineage.reason())
                : new PositionIntelligenceView.Rationale("AVAILABLE", lineage.plan().thesis(),
                lineage.plan().confirmationConditions(), lineage.plan().invalidationConditions(),
                lineage.plan().managementRules(), null);

        PositionIntelligenceView.Comparisons comparisons = comparisons(position);
        ChallengeInput challenge = challenge(account.getAccountId(), actorId);
        TradeScenarioProjectionResponse scenarios = project(position, account, balance, equity, lineage.plan(), challenge);
        String status = position.valuationStatus() == PositionValuationStatus.FRESH ? "AVAILABLE" : "PARTIAL";
        return new PositionIntelligenceView(position, status, lineage.view(), rationale, comparisons,
                scenarios, accountStatus(balance, equity), marketStatus(position),
                challengeStatus(challenge), clock.instant());
    }

    private LineageResult resolveLineage(UUID actorId, Account account, OpenPositionDashboardView position) {
        if (position.source() != PositionSource.TRADING_CORE) {
            return LineageResult.unavailable("LIVE_POSITION_LINEAGE_NOT_PERSISTED");
        }

        UUID tradeId;
        try {
            tradeId = UUID.fromString(position.positionId());
        } catch (IllegalArgumentException invalidId) {
            return LineageResult.unavailable("PAPER_POSITION_ID_INVALID");
        }

        TradeOutcome outcome = outcomes.findByTradeId(tradeId)
                .filter(value -> account.getAccountId().equals(value.accountId()))
                .orElse(null);
        if (outcome == null) return LineageResult.unavailable("TRADE_OUTCOME_NOT_FOUND");

        try {
            var plan = tradePlans.load(outcome.tradePlanId(), outcome.tradePlanVersion());
            if (!actorId.equals(plan.authorId()) || !account.getAccountId().equals(plan.tradingAccountId())) {
                return LineageResult.unavailable("TRADE_PLAN_NOT_ACCESSIBLE");
            }
            if (!opportunitiesBelongToAccount(plan, account.getAccountId())) {
                return LineageResult.unavailable("OPPORTUNITY_PROVENANCE_UNAVAILABLE");
            }
            var view = new PositionIntelligenceView.Lineage("AVAILABLE", outcome.id(), outcome.executionIntentId(),
                    plan.id(), plan.version(), plan.origin(), plan.opportunityIds(), null);
            return new LineageResult(view, plan, null);
        } catch (RuntimeException failure) {
            return LineageResult.unavailable("TRADE_PLAN_UNAVAILABLE");
        }
    }

    private PositionIntelligenceView.Comparisons comparisons(OpenPositionDashboardView position) {
        List<String> reasons = new ArrayList<>();
        BigDecimal priceChange = null;
        BigDecimal percentage = null;
        if (position.entryPrice() == null || position.currentPrice() == null) {
            reasons.add("CURRENT_PRICE_UNAVAILABLE");
        } else {
            priceChange = position.currentPrice().subtract(position.entryPrice());
            if (position.entryPrice().signum() != 0) {
                percentage = priceChange.multiply(BigDecimal.valueOf(100))
                        .divide(position.entryPrice(), 8, java.math.RoundingMode.HALF_UP);
            }
        }
        if (position.currentPrice() == null || position.stopLoss() == null) reasons.add("STOP_DISTANCE_UNAVAILABLE");
        if (position.currentPrice() == null || position.takeProfit() == null) reasons.add("TARGET_DISTANCE_UNAVAILABLE");
        BigDecimal distanceToStop = position.currentPrice() == null || position.stopLoss() == null
                ? null : position.currentPrice().subtract(position.stopLoss()).abs();
        BigDecimal distanceToTarget = position.currentPrice() == null || position.takeProfit() == null
                ? null : position.takeProfit().subtract(position.currentPrice()).abs();
        return new PositionIntelligenceView.Comparisons(reasons.isEmpty() ? "AVAILABLE" : "PARTIAL",
                priceChange, percentage, distanceToStop, distanceToTarget, reasons);
    }

    private TradeScenarioProjectionResponse project(OpenPositionDashboardView position, Account account,
                                                    BigDecimal balance, BigDecimal equity,
                                                    MarketIntelligenceTradePlanningClient.PlanTransport plan,
                                                    ChallengeInput challenge) {
        BigDecimal conversion = position.quoteAsset() != null && account.getBaseCurrency() != null
                && position.quoteAsset().equalsIgnoreCase(account.getBaseCurrency()) ? BigDecimal.ONE : null;
        String direction = position.side() == TradeType.BUY ? "LONG" : "SHORT";
        var input = new TradeScenarioProjectionEngine.Input(direction, position.currentPrice(), position.stopLoss(),
                position.takeProfit(), position.quantity(), balance, equity, challenge.dailyRemaining(),
                challenge.totalRemaining(), challenge.dailyLimit(), challenge.totalLimit(), challenge.value(),
                challenge.target(), conversion, challenge.applicable(), challenge.riskAvailable(), challenge.reason());
        var projection = scenarioEngine.project(input, clock.instant());
        var provenance = new TradeScenarioProjectionResponse.Provenance(account.getVersion(), account.getValuedAt(),
                null, null, position.priceOccurredAt(), null, challenge.id(), challenge.evaluatedAt(),
                challenge.evaluatedAt());
        TradeScenarioProjectionResponse response = TradeScenarioProjectionResponse.from(account.getAccountId(),
                position.priceOccurredAt(), projection, provenance);
        return plan == null ? response : response.withPlan(plan.id(), plan.version());
    }

    private boolean opportunitiesBelongToAccount(MarketIntelligenceTradePlanningClient.PlanTransport plan,
                                                  UUID accountId) {
        if (plan.opportunityIds() == null || plan.opportunityIds().isEmpty()) return true;
        var context = plan.originContext();
        return context != null && context.returnAvailable()
                && accountId.equals(context.accountId())
                && context.opportunityId() != null
                && plan.opportunityIds().contains(context.opportunityId());
    }

    private PositionIntelligenceView.SourceStatus accountStatus(BigDecimal balance, BigDecimal equity) {
        if (balance != null && equity != null) {
            return new PositionIntelligenceView.SourceStatus("AVAILABLE", null, null);
        }
        return new PositionIntelligenceView.SourceStatus("UNAVAILABLE", null,
                balance == null ? "ACCOUNT_BALANCE_UNAVAILABLE" : "ACCOUNT_EQUITY_UNAVAILABLE");
    }

    private PositionIntelligenceView.SourceStatus marketStatus(OpenPositionDashboardView position) {
        String status = position.valuationStatus() == PositionValuationStatus.FRESH
                ? "AVAILABLE" : position.valuationStatus().name();
        return new PositionIntelligenceView.SourceStatus(status, position.priceOccurredAt(),
                position.valuationStatus() == PositionValuationStatus.FRESH ? null : status);
    }

    private PositionIntelligenceView.SourceStatus challengeStatus(ChallengeInput value) {
        if (!value.applicable()) return new PositionIntelligenceView.SourceStatus("NOT_APPLICABLE", null, null);
        return new PositionIntelligenceView.SourceStatus(value.riskAvailable() ? "AVAILABLE" : "UNAVAILABLE",
                value.evaluatedAt(), value.reason());
    }

    private ChallengeInput challenge(UUID accountId, UUID actorId) {
        try {
            ChallengeProgress value = challenges.get(accountId, actorId);
            boolean riskAvailable = value.dailyLossHeadroom() != null && value.totalLossHeadroom() != null
                    && value.dailyLossHeadroom().status() == com.hope.trading.trading_core.challenge.application.RiskHeadroom.Status.AVAILABLE
                    && value.totalLossHeadroom().status() == com.hope.trading.trading_core.challenge.application.RiskHeadroom.Status.AVAILABLE;
            return new ChallengeInput(value.challengeId(), true, value.currentValue(), value.targetValue(),
                    value.dailyLossHeadroom() == null ? null : value.dailyLossHeadroom().remaining(),
                    value.totalLossHeadroom() == null ? null : value.totalLossHeadroom().remaining(),
                    value.dailyLossHeadroom() == null ? null : value.dailyLossHeadroom().limit(),
                    value.totalLossHeadroom() == null ? null : value.totalLossHeadroom().limit(), riskAvailable,
                    riskAvailable ? null : "CHALLENGE_RISK_CONTEXT_UNAVAILABLE",
                    value.evaluatedAt());
        } catch (ChallengeNotStartedException missing) {
            return ChallengeInput.notApplicable();
        } catch (RuntimeException unavailable) {
            return new ChallengeInput(null, true, null, null, null, null, null, null, false,
                    "CHALLENGE_CONTEXT_UNAVAILABLE", null);
        }
    }

    private record LineageResult(PositionIntelligenceView.Lineage view,
                                 MarketIntelligenceTradePlanningClient.PlanTransport plan, String reason) {
        static LineageResult unavailable(String reason) {
            return new LineageResult(new PositionIntelligenceView.Lineage("UNAVAILABLE", null, null, null, null,
                    null, Set.of(), reason), null, reason);
        }
    }

    private record ChallengeInput(UUID id, boolean applicable, BigDecimal value, BigDecimal target,
                                  BigDecimal dailyRemaining, BigDecimal totalRemaining, BigDecimal dailyLimit,
                                  BigDecimal totalLimit, boolean riskAvailable, String reason, Instant evaluatedAt) {
        static ChallengeInput notApplicable() {
            return new ChallengeInput(null, false, null, null, null, null, null, null, false, null, null);
        }
    }
}
