package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionAttempt;
import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.challenge.application.ChallengeReevaluationService;
import org.springframework.beans.factory.annotation.Autowired;
import com.hope.trading.trading_core.service.TradingCalculatorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaperSettlementService {
    private static final Logger log = LoggerFactory.getLogger(PaperSettlementService.class);

    private final BrokerAccountRepository brokerAccountRepository;
    private final AccountRepository accountRepository;
    private final TradingCalculatorService tradingCalculatorService;
    private final TradePlanRiskPort tradePlans;
    private final ChallengeReevaluationService challengeReevaluation;

    public PaperSettlementService(BrokerAccountRepository brokerAccountRepository,
                                   AccountRepository accountRepository) {
        this(brokerAccountRepository, accountRepository, new com.hope.trading.trading_core.service.TradingCalculatorServiceImpl(), null, null);
    }

    public PaperSettlementService(BrokerAccountRepository brokerAccountRepository,
                                   AccountRepository accountRepository,
                                   TradingCalculatorService tradingCalculatorService) {
        this(brokerAccountRepository, accountRepository, tradingCalculatorService, null, null);
    }

    @Autowired
    public PaperSettlementService(BrokerAccountRepository brokerAccountRepository,
                                   AccountRepository accountRepository,
                                   TradingCalculatorService tradingCalculatorService,
                                   TradePlanRiskPort tradePlans) {
        this(brokerAccountRepository, accountRepository, tradingCalculatorService, tradePlans, null);
    }

    public PaperSettlementService(BrokerAccountRepository brokerAccountRepository,
                                  AccountRepository accountRepository,
                                  TradingCalculatorService tradingCalculatorService,
                                  TradePlanRiskPort tradePlans,
                                  ChallengeReevaluationService challengeReevaluation) {
        this.brokerAccountRepository = brokerAccountRepository;
        this.accountRepository = accountRepository;
        this.tradingCalculatorService = tradingCalculatorService;
        this.tradePlans = tradePlans;
        this.challengeReevaluation = challengeReevaluation;
    }

    @Transactional
    public void settle(ExecutionIntent intent, ExecutionAttempt attempt, BrokerOrder order) {
        Optional<com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount> brokerAccountOpt =
                brokerAccountRepository.findById(intent.brokerAccountId());
        if (brokerAccountOpt.isEmpty()) {
            return;
        }
        com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount brokerAccount = brokerAccountOpt.get();
        if (brokerAccount.executionMode() != ExecutionMode.PAPER) {
            return;
        }

        Optional<Account> accountOpt = accountRepository.findByBrokerAccountId(brokerAccount.id());
        if (accountOpt.isEmpty()) {
            return;
        }
        Account account = accountOpt.get();
        if (account.getUser() == null || !brokerAccount.ownerId().equals(account.getUser().getUserId())
                || (account.getBroker() != null && !brokerAccount.provider().name().equals(account.getBroker()))) {
            throw new IllegalStateException("PAPER account identity is inconsistent");
        }
        ExecutionParameters params = intent.parameters();
        BrokerOrder.Fill fill = getFill(order);

        if (intent.purpose() == com.hope.trading.trading_core.execution.domain.model.ExecutionPurpose.EXIT) {
            settleExit(account, intent, fill);
        } else {
            updateBalances(account, intent, params.side(), fill.quantity(), fill.price(), fill.fee(), params);
            updatePosition(account, intent, params.side(), fill.quantity(), fill.price(), fill.executedAt(), params);
            recalculateEquity(account, fill.fee());
        }

        accountRepository.save(account);
        if (challengeReevaluation != null) {
            accountRepository.flush();
            try {
                challengeReevaluation.reevaluate(account);
            } catch (RuntimeException monitoringFailure) {
                log.error("PAPER settlement completed but Challenge Risk reevaluation failed for account {}",
                        account.getAccountId(), monitoringFailure);
            }
        }
    }

    private void settleExit(Account account, ExecutionIntent intent, BrokerOrder.Fill fill) {
        UUID targetId = intent.targetTradeId().orElseThrow(() -> new IllegalStateException("EXIT target is required"));
        Trade trade = account.getTrades().stream().filter(candidate -> targetId.equals(candidate.getTradeId()))
                .findFirst().orElseThrow(() -> new IllegalStateException("EXIT target trade not found"));
        if (trade.getTradeStatus() != TradeStatus.OPEN || trade.getClosedAt() != null) {
            throw new IllegalStateException("EXIT target trade is already closed");
        }
        if (fill.quantity().compareTo(trade.getQuantity()) != 0) {
            throw new IllegalStateException("EXIT fill quantity must equal target quantity");
        }
        ExecutionParameters.Side expectedSide = trade.getType() == TradeType.BUY
                ? ExecutionParameters.Side.SELL : ExecutionParameters.Side.BUY;
        if (intent.parameters().side() != expectedSide) {
            throw new IllegalStateException("EXIT side does not match target trade");
        }

        updateExitBalances(account, trade, fill);
        BigDecimal pnl = tradingCalculatorService.calculatePnL(trade.getType(), trade.getEntryPrice(),
                fill.price(), trade.getQuantity());
        trade.setExitPrice(fill.price());
        trade.setCurrentPrice(fill.price());
        trade.setClosedAt(fill.executedAt());
        trade.setPnl(pnl);
        trade.setTradeStatus(TradeStatus.CLOSED);
        account.setEquity(account.getEquity().add(pnl).subtract(fill.fee()));
        if (account.getEquity().compareTo(account.getPeakEquity()) > 0) {
            account.setPeakEquity(account.getEquity());
        }
    }

    private void updateExitBalances(Account account, Trade trade, BrokerOrder.Fill fill) {
        String base = extractBaseAsset(trade.getSymbol());
        String quote = extractQuoteAsset(trade.getSymbol(), account);
        BigDecimal notional = fill.price().multiply(fill.quantity());
        if (trade.getType() == TradeType.BUY) {
            deductBalance(account, base, fill.quantity());
            addBalance(account, quote, notional.subtract(fill.fee()));
        } else {
            addBalance(account, base, fill.quantity());
            deductBalance(account, quote, notional.add(fill.fee()));
        }
    }

    private BrokerOrder.Fill getFill(BrokerOrder order) {
        if (order.fills().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot settle PAPER execution without fill: order " + order.id());
        }
        return order.fills().get(order.fills().size() - 1);
    }

    private void updateBalances(Account account, ExecutionIntent intent, ExecutionParameters.Side side, BigDecimal quantity,
                                 BigDecimal fillPrice, BigDecimal fee, ExecutionParameters params) {
        BigDecimal notional = fillPrice.multiply(quantity);
        String quote = extractQuoteAsset(params.instrument(), account);

        if (side == ExecutionParameters.Side.BUY) {
            deductBalance(account, quote, notional.add(fee));
            addBalance(account, extractBaseAsset(params.instrument()), quantity);
        } else {
            authorizeShortMargin(account, intent, params, notional);
            // The negative base balance records borrowed inventory and is settled by
            // the later BUY exit.
            deductBalance(account, extractBaseAsset(params.instrument()), quantity, true);
            addBalance(account, quote, notional.subtract(fee));
        }
    }

    private void authorizeShortMargin(Account account, ExecutionIntent intent,
                                      ExecutionParameters params, BigDecimal notional) {
        if (intent.riskApproval() == null
                || (intent.riskApproval().decision() != com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference.Decision.APPROVED
                && intent.riskApproval().decision() != com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference.Decision.APPROVED_WITH_WARNINGS)) {
            throw new IllegalStateException("PAPER short margin authorization unavailable");
        }
        TradePlanRiskPort.Snapshot plan = resolvePlan(intent);
        if (plan == null || !"SHORT".equalsIgnoreCase(plan.direction())
                || !params.instrument().equalsIgnoreCase(plan.instrument())) {
            throw new IllegalStateException("PAPER short margin authorization unavailable");
        }
        if (account.getEquity() == null || account.getEquity().compareTo(notional) < 0) {
            throw new IllegalStateException("PAPER short margin collateral insufficient");
        }
        String quote = extractQuoteAsset(params.instrument(), account);
        BigDecimal availableCollateral = account.getBalances().stream()
                .filter(balance -> quote.equals(balance.getAsset()))
                .map(AccountBalance::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        if (availableCollateral.compareTo(notional) < 0) {
            throw new IllegalStateException("PAPER short margin collateral insufficient");
        }
    }

    private String extractBaseAsset(String instrument) {
        int slashIndex = instrument.indexOf('/');
        if (slashIndex > 0) {
            return instrument.substring(0, slashIndex);
        }
        return instrument;
    }

    private String extractQuoteAsset(String instrument, Account account) {
        int slashIndex = instrument.indexOf('/');
        if (slashIndex >= 0 && slashIndex < instrument.length() - 1) {
            String quote = instrument.substring(slashIndex + 1);
            boolean hasQuoteBalance = account.getBalances().stream()
                    .anyMatch(balance -> quote.equals(balance.getAsset()));
            if (hasQuoteBalance) return quote;
        }
        return account.getBaseCurrency();
    }

    private void updatePosition(Account account, ExecutionIntent intent, ExecutionParameters.Side side, BigDecimal quantity,
                                 BigDecimal fillPrice, Instant executedAt, ExecutionParameters params) {
        TradePlanRiskPort.Snapshot plan = resolvePlan(intent);
        BigDecimal stopPrice = plan == null ? null : plan.stopPrice();
        BigDecimal takeProfit = plan == null ? null : plan.takeProfit();
        Optional<Trade> existingTrade = account.getTrades().stream()
                .filter(t -> t.getSymbol().equals(params.instrument())
                        && t.getTradeStatus() == TradeStatus.OPEN
                        && t.getType() == (side == ExecutionParameters.Side.BUY ? TradeType.BUY : TradeType.SELL))
                .findFirst();

        if (existingTrade.isPresent()) {
            Trade trade = existingTrade.get();
            BigDecimal totalQuantity = trade.getQuantity().add(quantity);
            BigDecimal totalCost = trade.getEntryPrice().multiply(trade.getQuantity())
                    .add(fillPrice.multiply(quantity));
            BigDecimal newEntryPrice = totalCost.divide(totalQuantity, 8, java.math.RoundingMode.HALF_UP);
            trade.setQuantity(totalQuantity);
            trade.setEntryPrice(newEntryPrice);
            if (trade.getStopLoss() == null) trade.setStopLoss(stopPrice);
            if (trade.getTakeProfit() == null) trade.setTakeProfit(takeProfit);
        } else {
            Trade trade = Trade.builder()
                    .symbol(params.instrument())
                    .type(side == ExecutionParameters.Side.BUY ? TradeType.BUY : TradeType.SELL)
                    .entryPrice(fillPrice)
                    .quantity(quantity)
                    .currentPrice(fillPrice)
                    .openedAt(executedAt)
                    .stopLoss(stopPrice)
                    .takeProfit(takeProfit)
                    .tradeStatus(TradeStatus.OPEN)
                    .build();
            account.addTrade(trade);
        }
    }

    private TradePlanRiskPort.Snapshot resolvePlan(ExecutionIntent intent) {
        if (tradePlans == null || intent.tradePlan() == null) return null;
        return tradePlans.loadReady(intent.tradePlan().tradePlanId(), intent.tradePlan().version());
    }

    private void recalculateEquity(Account account, BigDecimal fee) {
        account.setEquity(account.getEquity().subtract(fee));
        if (account.getEquity().compareTo(account.getPeakEquity()) > 0) {
            account.setPeakEquity(account.getEquity());
        }
    }

    private void addBalance(Account account, String asset, BigDecimal amount) {
        Optional<AccountBalance> existing = account.getBalances().stream()
                .filter(b -> asset.equals(b.getAsset()))
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setAmount(existing.get().getAmount().add(amount));
        } else {
            AccountBalance balance = new AccountBalance();
            balance.setAsset(asset);
            balance.setAmount(amount);
            account.addBalance(balance);
        }
    }

    private void deductBalance(Account account, String asset, BigDecimal amount) {
        deductBalance(account, asset, amount, false);
    }

    private void deductBalance(Account account, String asset, BigDecimal amount, boolean allowBorrow) {
        Optional<AccountBalance> existing = account.getBalances().stream()
                .filter(b -> asset.equals(b.getAsset()))
                .findFirst();
        if (existing.isEmpty()) {
            if (allowBorrow) {
                addBalance(account, asset, amount.negate());
                return;
            }
            throw new IllegalStateException("PAPER balance unavailable: " + asset);
        }
        if (!allowBorrow && existing.get().getAmount().compareTo(amount) < 0) {
            throw new IllegalStateException("PAPER balance insufficient: " + asset);
        }
        existing.get().setAmount(existing.get().getAmount().subtract(amount));
    }
}
