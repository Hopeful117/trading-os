package com.hope.trading.trading_core.risk.application;

import com.hope.trading.risk.projection.TradeScenarioProjectionEngine;
import com.hope.trading.trading_core.challenge.application.ChallengeNotStartedException;
import com.hope.trading.trading_core.challenge.application.ChallengeProgress;
import com.hope.trading.trading_core.challenge.application.ChallengeProgressService;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.risk.application.port.MarketValuationPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeScenarioProjectionService {
    private final TradePlanRiskPort tradePlans;
    private final AccountRepository accounts;
    private final ChallengeProgressService challenges;
    private final MarketValuationPort valuation;
    private final Clock clock;
    private final TradeScenarioProjectionEngine engine = new TradeScenarioProjectionEngine();

    public TradeScenarioProjectionService(TradePlanRiskPort tradePlans, AccountRepository accounts,
                                          ChallengeProgressService challenges, MarketValuationPort valuation,
                                          Clock clock) {
        this.tradePlans = tradePlans;
        this.accounts = accounts;
        this.challenges = challenges;
        this.valuation = valuation;
        this.clock = clock;
    }

    @Transactional(readOnly = true, noRollbackFor = RiskEvaluationException.class)
    public TradeScenarioProjectionResponse project(UUID actorId, UUID tradePlanId, long version) {
        TradePlanRiskPort.Snapshot plan = tradePlans.loadPreflight(tradePlanId, version);
        if (!tradePlanId.equals(plan.tradePlanId()) || version != plan.tradePlanVersion()) {
            throw new RiskEvaluationException("TRADE_PLAN_VERSION_MISMATCH",
                    "Loaded Trade Plan does not match the requested identity", 409);
        }
        if (!actorId.equals(plan.ownerId())) {
            throw new RiskEvaluationException("TRADE_PLAN_FORBIDDEN", "Trade Plan does not belong to the actor", 403);
        }
        Account account = accounts.findById(plan.tradingAccountId())
                .orElseThrow(() -> new RiskEvaluationException("ACCOUNT_NOT_FOUND", "Account not found", 404));
        if (account.getUser() == null || !actorId.equals(account.getUser().getUserId())) {
            throw new RiskEvaluationException("ACCOUNT_FORBIDDEN", "Account does not belong to the actor", 403);
        }
        if (!Objects.equals(account.getBaseCurrency(), plan.accountCurrency())) {
            throw new RiskEvaluationException("ACCOUNT_CURRENCY_MISMATCH",
                    "Trade Plan and account currencies do not match", 422);
        }

        Optional<ChallengeProgress> challenge = challenge(account.getAccountId(), actorId);
        BigDecimal balance = balance(account);
        BigDecimal equity = currentEquity(account);
        Instant observedAt = account.getValuedAt() != null ? account.getValuedAt() : account.getMarketDataTimestamp();
        ValuationFacts market = valuation(plan, account.getBaseCurrency());
        TradeScenarioProjectionEngine.Input input = new TradeScenarioProjectionEngine.Input(
                plan.direction(), plan.entryIntent() == null ? null : plan.entryIntent().price(),
                plan.stopPrice(), plan.takeProfit(), plan.quantity(), balance, equity,
                challenge.map(TradeScenarioProjectionService::dailyRemaining).orElse(null),
                challenge.map(TradeScenarioProjectionService::totalRemaining).orElse(null),
                challenge.map(TradeScenarioProjectionService::dailyLimit).orElse(null),
                challenge.map(TradeScenarioProjectionService::totalLimit).orElse(null),
                challenge.map(ChallengeProgress::currentValue).orElse(null),
                challenge.map(ChallengeProgress::targetValue).orElse(null),
                market.quoteToReportingRate(), challenge.isPresent(), challengeRiskAvailable(challenge),
                challengeRiskReason(challenge));
        var projection = engine.project(input, clock.instant());
        var provenance = new TradeScenarioProjectionResponse.Provenance(
                account.getVersion(), observedAt, market.snapshotId(), market.sourceVersion(),
                market.valuationTimestamp(), market.policyVersion(),
                challenge.map(ChallengeProgress::challengeId).orElse(null),
                challenge.map(TradeScenarioProjectionService::riskContextEvaluatedAt).orElse(null),
                challenge.map(ChallengeProgress::evaluatedAt).orElse(null));
        return TradeScenarioProjectionResponse.from(plan.tradingAccountId(), observedAt, projection, provenance)
                .withPlan(tradePlanId, version);
    }

    private Optional<ChallengeProgress> challenge(UUID accountId, UUID actorId) {
        try {
            return Optional.of(challenges.get(accountId, actorId));
        } catch (ChallengeNotStartedException missing) {
            return Optional.empty();
        }
    }

    private BigDecimal balance(Account account) {
        if (account.getRealizedBalance() != null) return account.getRealizedBalance();
        if (account.getBalances() == null) return null;
        return account.getBalances().stream()
                .filter(value -> value != null && account.getBaseCurrency().equalsIgnoreCase(value.getAsset()))
                .map(value -> value.getAmount())
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    private BigDecimal currentEquity(Account account) {
        return account.getEquity() == null || account.getValuationStatus() == null
                || !"FRESH".equals(account.getValuationStatus().name()) ? null : account.getEquity();
    }

    private static BigDecimal dailyRemaining(ChallengeProgress value) {
        return value.dailyLossHeadroom() == null ? null : value.dailyLossHeadroom().remaining();
    }

    private static BigDecimal totalRemaining(ChallengeProgress value) {
        return value.totalLossHeadroom() == null ? null : value.totalLossHeadroom().remaining();
    }

    private static BigDecimal dailyLimit(ChallengeProgress value) {
        return value.dailyLossHeadroom() == null ? null : value.dailyLossHeadroom().limit();
    }

    private static BigDecimal totalLimit(ChallengeProgress value) {
        return value.totalLossHeadroom() == null ? null : value.totalLossHeadroom().limit();
    }

    private static boolean challengeRiskAvailable(Optional<ChallengeProgress> value) {
        return value.isPresent()
                && value.get().dailyLossHeadroom() != null
                && value.get().totalLossHeadroom() != null
                && value.get().dailyLossHeadroom().status() == com.hope.trading.trading_core.challenge.application.RiskHeadroom.Status.AVAILABLE
                && value.get().totalLossHeadroom().status() == com.hope.trading.trading_core.challenge.application.RiskHeadroom.Status.AVAILABLE;
    }

    private static String challengeRiskReason(Optional<ChallengeProgress> value) {
        if (value.isEmpty()) return null;
        if (value.get().dailyLossHeadroom() != null
                && value.get().dailyLossHeadroom().status() != com.hope.trading.trading_core.challenge.application.RiskHeadroom.Status.AVAILABLE) {
            return value.get().dailyLossHeadroom().reason();
        }
        if (value.get().totalLossHeadroom() != null
                && value.get().totalLossHeadroom().status() != com.hope.trading.trading_core.challenge.application.RiskHeadroom.Status.AVAILABLE) {
            return value.get().totalLossHeadroom().reason();
        }
        return "CHALLENGE_RISK_CONTEXT_UNAVAILABLE";
    }

    private static Instant riskContextEvaluatedAt(ChallengeProgress value) {
        Instant daily = value.dailyLossHeadroom() == null ? null : value.dailyLossHeadroom().evaluatedAt();
        Instant total = value.totalLossHeadroom() == null ? null : value.totalLossHeadroom().evaluatedAt();
        return daily != null ? daily : total;
    }

    private ValuationFacts valuation(TradePlanRiskPort.Snapshot plan, String reportingCurrency) {
        try {
            var snapshot = valuation.value(reportingCurrency, clock.instant(),
                    List.of(new MarketValuationPort.Instrument(plan.instrument(), plan.instrument(),
                            MarketValuationPort.PriceUse.LAST)), List.of(),
                    new MarketValuationPort.FreshnessRequirement(
                            MarketValuationPort.DecisionBoundary.PRE_TRADE,
                            MarketValuationPort.AcquisitionMode.REUSE_OR_ACQUIRE,
                            Duration.ofMinutes(5), Duration.ofSeconds(18)));
            var fact = snapshot.facts() == null ? null : snapshot.facts().stream()
                    .filter(value -> plan.instrument().equalsIgnoreCase(value.id())
                            || plan.instrument().equalsIgnoreCase(value.asset()))
                    .findFirst().orElse(null);
            return new ValuationFacts(snapshot.snapshotId(), snapshot.sourceVersion(), snapshot.valuationTimestamp(),
                    snapshot.policyVersion(), snapshot.complete() && fact != null && fact.quoteToReportingRate() != null
                            ? fact.quoteToReportingRate() : null);
        } catch (RuntimeException failure) {
            return ValuationFacts.unavailable();
        }
    }

    private record ValuationFacts(UUID snapshotId, long sourceVersion, Instant valuationTimestamp,
                                  String policyVersion, BigDecimal quoteToReportingRate) {
        static ValuationFacts unavailable() { return new ValuationFacts(null, 0, null, null, null); }
    }
}
