package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.broker.apiClient.BrokerApiClient;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.dashboard.integration.*;
import com.hope.trading.trading_core.dashboard.model.*;
import com.hope.trading.trading_core.market_data.dto.*;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.service.AccountService;
import com.hope.trading.trading_core.service.RiskEngine;
import com.hope.trading.trading_core.service.TradeAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardQueryService {
    private final AccountService accountService;
    private final BrokerAccountRepository brokerAccounts;
    private final BrokerApiClient brokerApiClient;
    private final BrokerDashboardMapper brokerMapper;
    private final PositionQueryService positionQueryService;
    private final AccountBalanceValuationService balanceValuationService;
    private final AccountEquityService equityService;
    private final DashboardFreshnessService freshnessService;
    private final DashboardAlertService alertService;
    private final TradeAnalyticsService tradeAnalyticsService;
    private final RiskEngine riskEngine;

    @Transactional(readOnly = true)
    public DashboardSummary findDashboard(UUID accountId, String username) {
        Account account = accountService.getAccountById(accountId, username);
        Instant generatedAt = Instant.now();

        boolean paper = isPaperAccount(account);
        BrokerAccountFact broker;
        try {
            broker = paper
                    ? paperFact(account, generatedAt)
                    : brokerMapper.toFact(brokerApiClient.getAccount());
        } catch (RuntimeException exception) {
            log.warn("Dashboard account data unavailable accountId={}", accountId);
            return unavailable(account, generatedAt);
        }

        Map<String, BigDecimal> balances = paper
                ? persistedBalances(account) : broker.balances();
        BigDecimal balance = balances.getOrDefault(account.getBaseCurrency(), BigDecimal.ZERO);
        List<String> warnings = new ArrayList<>();
        AccountValuationResult balanceValuation = broker.balancesAvailable()
                ? balanceValuationService.value(account.getBaseCurrency(), balances)
                : AccountValuationResult.unavailable("Soldes de compte indisponibles");
        if (balanceValuation.warning() != null) warnings.add(balanceValuation.warning());
        boolean brokerStale = broker.dataAt() != null
                && broker.dataAt().isBefore(generatedAt.minus(DashboardFreshnessService.STALE_AFTER));

        List<OpenPositionDashboardView> initialPositions = paper
                ? positionQueryService.findPaperPositions(account, account.getEquity(), generatedAt)
                : positionQueryService.findPositions(accountId, broker.positions(), balance, generatedAt,
                account.getBaseCurrency());
        BigDecimal unrealizedPnl = positionPnl(initialPositions, broker.positionPnlTreatment());
        AccountEquityResult equity = equityService.select(
                balanceValuation, unrealizedPnl, broker.brokerEquity(), broker.brokerEquityTotal(), brokerStale
        );
        if (equity.equity() == null) {
            return unavailableEquity(account, broker, balance, equity, initialPositions,
                    warnings, generatedAt);
        }

        List<OpenPositionDashboardView> positions = paper
                ? positionQueryService.findPaperPositions(account, equity.equity(), generatedAt)
                : positionQueryService.findPositions(accountId, broker.positions(), equity.equity(), generatedAt,
                account.getBaseCurrency());
        BigDecimal dailyPnl = tradeAnalyticsService.getTodayPnL(accountId);
        BigDecimal drawdown = positive(account.getPeakEquity().subtract(equity.equity()));
        BigDecimal usedRisk = positions.stream()
                .map(OpenPositionDashboardView::riskAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dailyPnlPercentage = percentage(dailyPnl, balance);
        BigDecimal drawdownPercentage = percentage(drawdown, account.getPeakEquity());
        BigDecimal usedRiskPercentage = percentage(usedRisk, equity.equity());

        DashboardRiskEvaluation riskEvaluation = riskEngine.evaluateDashboard(
                account, positive(dailyPnlPercentage.negate()), drawdownPercentage, usedRiskPercentage
        );
        RiskDashboardSummary risk = riskSummary(
                account, riskEvaluation, usedRisk, usedRiskPercentage,
                dailyPnl, dailyPnlPercentage, drawdown, drawdownPercentage, equity.equity()
        );

        DashboardFreshness freshness = freshnessService.evaluate(
                broker.dataAt(),
                List.of(),
                true,
                marketDataAvailable(balanceValuation, positions),
                !broker.positions().isEmpty() || !broker.balances().isEmpty(),
                warnings
        );
        List<DashboardAlert> alerts = alertService.build(
                positions, freshness, risk, equity.divergent()
        );

        AccountDashboardSummary accountSummary = new AccountDashboardSummary(
                accountId, account.getName(), broker.broker(), account.getBaseCurrency(),
                balance, equity.equity(), dailyPnl, dailyPnlPercentage,
                drawdown, drawdownPercentage, equity.source(), equity.valuationStatus(),
                equity.valuationTimestamp(), equity.valuationPolicyVersion()
        );

        return new DashboardSummary(
                accountSummary, risk, positions, alerts, List.of(), freshness, generatedAt
        );
    }

    private boolean marketDataAvailable(AccountValuationResult balanceValuation,
                                        List<OpenPositionDashboardView> positions) {
        return "COMPLETE".equals(balanceValuation.status())
                && positions.stream().allMatch(position ->
                position.valuationStatus() == PositionValuationStatus.FRESH);
    }

    private DashboardSummary unavailable(Account account, Instant generatedAt) {
        DashboardFreshness freshness = freshnessService.evaluate(
                null, List.of(), false, false, false, List.of()
        );
        AccountDashboardSummary summary = new AccountDashboardSummary(
                account.getAccountId(), account.getName(), account.getBroker(),
                account.getBaseCurrency(), null, null, null, null,
                null, null, "UNAVAILABLE", "UNAVAILABLE", null, null
        );
        RiskDashboardSummary risk = new RiskDashboardSummary(
                RiskStatus.UNAVAILABLE, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                ruleValue(account, RuleType.DAILY), BigDecimal.ZERO, BigDecimal.ZERO,
                ruleValue(account, RuleType.DRAWDOWN), List.of()
        );
        return new DashboardSummary(
                summary, risk, List.of(),
                alertService.build(List.of(), freshness, risk, false),
                List.of(), freshness, generatedAt
        );
    }

    private DashboardSummary unavailableEquity(
            Account account,
            BrokerAccountFact broker,
            BigDecimal balance,
            AccountEquityResult equity,
            List<OpenPositionDashboardView> positions,
            List<String> warnings,
            Instant generatedAt
    ) {
        DashboardFreshness freshness = freshnessService.evaluate(
                broker.dataAt(), List.of(), true, false, true, warnings);
        AccountDashboardSummary summary = new AccountDashboardSummary(
                account.getAccountId(), account.getName(), broker.broker(), account.getBaseCurrency(),
                balance, null, null, null, null, null, equity.source(),
                equity.valuationStatus(), equity.valuationTimestamp(), equity.valuationPolicyVersion()
        );
        RiskDashboardSummary risk = new RiskDashboardSummary(
                RiskStatus.UNAVAILABLE, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                ruleValue(account, RuleType.DAILY), BigDecimal.ZERO, BigDecimal.ZERO,
                ruleValue(account, RuleType.DRAWDOWN), List.of()
        );
        return new DashboardSummary(
                summary, risk, positions,
                alertService.build(positions, freshness, risk, equity.divergent()),
                List.of(), freshness, generatedAt
        );
    }

    private RiskDashboardSummary riskSummary(
            Account account, DashboardRiskEvaluation evaluation,
            BigDecimal usedRisk, BigDecimal usedRiskPercentage,
            BigDecimal dailyPnl, BigDecimal dailyPnlPercentage,
            BigDecimal drawdown, BigDecimal drawdownPercentage,
            BigDecimal equity
    ) {
        BigDecimal riskLimitPercentage = ruleValue(account, RuleType.RISK);
        BigDecimal riskLimitAmount = equity.multiply(riskLimitPercentage)
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        return new RiskDashboardSummary(
                evaluation.status(), usedRisk, usedRiskPercentage,
                positive(riskLimitAmount.subtract(usedRisk)),
                positive(riskLimitPercentage.subtract(usedRiskPercentage)),
                positive(dailyPnl.negate()), positive(dailyPnlPercentage.negate()),
                ruleValue(account, RuleType.DAILY), drawdown, drawdownPercentage,
                ruleValue(account, RuleType.DRAWDOWN), evaluation.rules()
        );
    }

    private BigDecimal ruleValue(Account account, RuleType type) {
        if (account.getRules() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal fraction = switch (type) {
            case RISK -> account.getRules().getMaxRiskPerTrade();
            case DAILY -> account.getRules().getMaxDailyLoss();
            case DRAWDOWN -> account.getRules().getMaxTotalDrawdown();
        };
        return fraction == null ? BigDecimal.ZERO : fraction.multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal persistedBalance(Account account) {
        return account.getBalances().stream()
                .filter(balance -> balance.getAsset().equalsIgnoreCase(account.getBaseCurrency()))
                .map(AccountBalance::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private Map<String, BigDecimal> persistedBalances(Account account) {
        return account.getBalances().stream()
                .filter(balance -> balance.getAsset() != null)
                .collect(Collectors.toMap(AccountBalance::getAsset, AccountBalance::getAmount,
                        (first, ignored) -> first));
    }

    private BigDecimal positionPnl(List<OpenPositionDashboardView> positions,
                                   PositionPnlTreatment treatment) {
        if (treatment == PositionPnlTreatment.INCLUDED_IN_BALANCES) return BigDecimal.ZERO;
        if (positions.stream().anyMatch(position -> position.unrealizedPnl() == null
                || position.valuationStatus() == PositionValuationStatus.UNSUPPORTED_CURRENCY)) {
            return null;
        }
        return positions.stream()
                .map(OpenPositionDashboardView::unrealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isPaperAccount(Account account) {
        return account.getBrokerAccountId() != null
                && Optional.ofNullable(brokerAccounts.findById(account.getBrokerAccountId()))
                .flatMap(value -> value)
                .map(broker -> broker.executionMode() == ExecutionMode.PAPER)
                .orElse(false);
    }

    private BrokerAccountFact paperFact(Account account, Instant generatedAt) {
        return new BrokerAccountFact(
                account.getBrokerAccountId() == null ? account.getAccountId().toString()
                        : account.getBrokerAccountId().toString(),
                account.getBroker(), account.getBaseCurrency(), persistedBalances(account), true,
                null, false, List.of(), generatedAt, PositionPnlTreatment.INCLUDED_IN_BALANCES);
    }

    private BigDecimal percentage(BigDecimal value, BigDecimal reference) {
        if (value == null || reference == null || reference.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(BigDecimal.valueOf(100))
                .divide(reference.abs(), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal positive(BigDecimal value) {
        return value.max(BigDecimal.ZERO);
    }

    private enum RuleType {
        RISK,
        DAILY,
        DRAWDOWN
    }
}
