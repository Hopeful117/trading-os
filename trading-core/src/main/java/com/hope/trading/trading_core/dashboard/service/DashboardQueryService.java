package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.broker.apiClient.BrokerApiClient;
import com.hope.trading.trading_core.dashboard.integration.*;
import com.hope.trading.trading_core.dashboard.model.*;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardQueryService {
    private final AccountService accountService;
    private final BrokerApiClient brokerApiClient;
    private final MarketDataClient marketDataClient;
    private final BrokerDashboardMapper brokerMapper;
    private final MarketDataDashboardMapper marketMapper;
    private final PositionValuationService valuationService;
    private final AccountEquityService equityService;
    private final DashboardFreshnessService freshnessService;
    private final DashboardAlertService alertService;
    private final TradeAnalyticsService tradeAnalyticsService;
    private final RiskEngine riskEngine;

    @Transactional(readOnly = true)
    public DashboardSummary findDashboard(UUID accountId, String username) {
        Account account = accountService.getAccountById(accountId, username);
        Instant generatedAt = Instant.now();

        BrokerAccountFact broker;
        try {
            broker = brokerMapper.toFact(brokerApiClient.getAccount());
        } catch (RuntimeException exception) {
            log.warn("Dashboard broker data unavailable accountId={}", accountId);
            return unavailable(account, generatedAt);
        }

        BigDecimal balance = broker.balances()
                .getOrDefault(account.getBaseCurrency(), persistedBalance(account));
        List<String> warnings = new ArrayList<>();
        MarketLookup marketLookup = loadMarkets(broker.positions(), warnings);
        Map<UUID, MarketPriceFact> prices = loadPrices(marketLookup.marketIds(), warnings);
        boolean marketAvailable = marketLookup.available() && prices != null;
        Map<UUID, MarketPriceFact> safePrices = prices == null ? Map.of() : prices;

        List<OpenPositionDashboardView> initialPositions = buildPositions(
                account, broker.positions(), marketLookup.bySymbol(), safePrices,
                balance, generatedAt
        );
        BigDecimal unrealizedPnl = initialPositions.stream()
                .map(OpenPositionDashboardView::unrealizedPnl)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean brokerStale = broker.dataAt() != null
                && broker.dataAt().isBefore(generatedAt.minus(DashboardFreshnessService.STALE_AFTER));
        AccountEquityResult equity = equityService.select(
                balance, unrealizedPnl, broker.brokerEquity(), brokerStale
        );

        List<OpenPositionDashboardView> positions = buildPositions(
                account, broker.positions(), marketLookup.bySymbol(), safePrices,
                equity.equity(), generatedAt
        );
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
                safePrices.values().stream().map(MarketPriceFact::occurredAt).toList(),
                true,
                marketAvailable,
                !broker.positions().isEmpty(),
                warnings
        );
        List<DashboardAlert> alerts = alertService.build(
                positions, freshness, risk, equity.divergent()
        );

        AccountDashboardSummary accountSummary = new AccountDashboardSummary(
                accountId, account.getName(), broker.broker(), account.getBaseCurrency(),
                balance, equity.equity(), dailyPnl, dailyPnlPercentage,
                drawdown, drawdownPercentage, equity.source()
        );
        List<MarketDashboardView> markets = safePrices.values().stream()
                .map(price -> new MarketDashboardView(
                        price.marketId(), price.symbol(), price.price(),
                        price.tradable(), price.occurredAt()
                ))
                .toList();

        return new DashboardSummary(
                accountSummary, risk, positions, alerts, markets, freshness, generatedAt
        );
    }

    private DashboardSummary unavailable(Account account, Instant generatedAt) {
        DashboardFreshness freshness = freshnessService.evaluate(
                null, List.of(), false, false, false, List.of()
        );
        AccountDashboardSummary summary = new AccountDashboardSummary(
                account.getAccountId(), account.getName(), account.getBroker(),
                account.getBaseCurrency(), null, null, null, null,
                null, null, "UNAVAILABLE"
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

    private MarketLookup loadMarkets(List<BrokerPositionFact> positions, List<String> warnings) {
        if (positions.isEmpty()) {
            return new MarketLookup(Map.of(), List.of(), true);
        }
        try {
            Map<String, MarketResponse> bySymbol = marketDataClient.findAll().stream()
                    .collect(Collectors.toMap(
                            market -> normalize(market.getSymbol()),
                            Function.identity(),
                            (first, ignored) -> first
                    ));
            Map<String, MarketResponse> resolved = positions.stream()
                    .map(BrokerPositionFact::symbol)
                    .distinct()
                    .filter(symbol -> bySymbol.containsKey(normalize(symbol)))
                    .collect(Collectors.toMap(
                            Function.identity(), symbol -> bySymbol.get(normalize(symbol))
                    ));
            positions.stream()
                    .map(BrokerPositionFact::symbol)
                    .filter(symbol -> !resolved.containsKey(symbol))
                    .forEach(symbol -> warnings.add("Marché interne introuvable pour " + symbol));
            return new MarketLookup(
                    resolved,
                    resolved.values().stream().map(MarketResponse::getMarketId).distinct().toList(),
                    true
            );
        } catch (RuntimeException exception) {
            log.warn("Dashboard market catalog unavailable");
            return new MarketLookup(Map.of(), List.of(), false);
        }
    }

    private Map<UUID, MarketPriceFact> loadPrices(List<UUID> marketIds, List<String> warnings) {
        if (marketIds.isEmpty()) {
            return Map.of();
        }
        try {
            return marketDataClient.findPriceSnapshots(new MarketPriceSnapshotRequest(marketIds))
                    .stream()
                    .map(marketMapper::toFact)
                    .peek(price -> {
                        if (price.status() != MarketPriceSnapshotStatus.FRESH) {
                            warnings.add("Prix indisponible pour le marché " + price.marketId());
                        }
                    })
                    .collect(Collectors.toMap(MarketPriceFact::marketId, Function.identity()));
        } catch (RuntimeException exception) {
            log.warn("Dashboard market prices unavailable");
            return null;
        }
    }

    private List<OpenPositionDashboardView> buildPositions(
            Account account,
            List<BrokerPositionFact> brokerPositions,
            Map<String, MarketResponse> markets,
            Map<UUID, MarketPriceFact> prices,
            BigDecimal equity,
            Instant calculatedAt
    ) {
        return brokerPositions.stream().map(position -> {
            MarketResponse market = markets.get(position.symbol());
            MarketPriceFact price = market == null ? null : prices.get(market.getMarketId());
            BigDecimal currentPrice = price != null
                    && price.status() == MarketPriceSnapshotStatus.FRESH
                    ? price.price() : null;
            PositionValuation value = valuationService.value(position, currentPrice, equity);
            return new OpenPositionDashboardView(
                    position.positionId(), account.getAccountId(),
                    market == null ? null : market.getMarketId(),
                    position.symbol(), position.side(), position.quantity(),
                    position.entryPrice(), currentPrice, position.stopLoss(), position.takeProfit(),
                    value.pnl(), value.pnlPercentage(), position.brokerUnrealizedPnl(),
                    value.riskAmount(), value.riskPercentage(), value.exposure(),
                    position.stopLoss() == null
                            ? PositionProtectionStatus.MISSING_STOP_LOSS
                            : PositionProtectionStatus.PROTECTED,
                    price != null && price.tradable(),
                    position.openedAt(), price == null ? null : price.occurredAt(), calculatedAt
            );
        }).toList();
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

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.toUpperCase(Locale.ROOT)
                .replace("XBT", "BTC")
                .replaceAll("[^A-Z0-9]", "");
    }

    private record MarketLookup(
            Map<String, MarketResponse> bySymbol,
            List<UUID> marketIds,
            boolean available
    ) {
    }

    private enum RuleType {
        RISK,
        DAILY,
        DRAWDOWN
    }
}
