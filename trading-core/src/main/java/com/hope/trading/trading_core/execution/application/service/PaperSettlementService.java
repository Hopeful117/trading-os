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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaperSettlementService {

    private final BrokerAccountRepository brokerAccountRepository;
    private final AccountRepository accountRepository;

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

        updateBalances(account, params.side(), fill.quantity(), fill.price(), fill.fee(), params);
        updatePosition(account, params.side(), fill.quantity(), fill.price(), fill.executedAt(), params);
        recalculateEquity(account, fill.fee());

        accountRepository.save(account);
    }

    private BrokerOrder.Fill getFill(BrokerOrder order) {
        if (order.fills().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot settle PAPER execution without fill: order " + order.id());
        }
        return order.fills().get(order.fills().size() - 1);
    }

    private void updateBalances(Account account, ExecutionParameters.Side side, BigDecimal quantity,
                                BigDecimal fillPrice, BigDecimal fee, ExecutionParameters params) {
        BigDecimal notional = fillPrice.multiply(quantity);

        if (side == ExecutionParameters.Side.BUY) {
            deductBalance(account, "USD", notional.add(fee));
            addBalance(account, extractBaseAsset(params.instrument()), quantity);
        } else {
            deductBalance(account, extractBaseAsset(params.instrument()), quantity);
            addBalance(account, "USD", notional.subtract(fee));
        }
    }

    private String extractBaseAsset(String instrument) {
        int slashIndex = instrument.indexOf('/');
        if (slashIndex > 0) {
            return instrument.substring(0, slashIndex);
        }
        return instrument;
    }

    private void updatePosition(Account account, ExecutionParameters.Side side, BigDecimal quantity,
                                BigDecimal fillPrice, Instant executedAt, ExecutionParameters params) {
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
        } else {
            Trade trade = Trade.builder()
                    .symbol(params.instrument())
                    .type(side == ExecutionParameters.Side.BUY ? TradeType.BUY : TradeType.SELL)
                    .entryPrice(fillPrice)
                    .quantity(quantity)
                    .currentPrice(fillPrice)
                    .openedAt(executedAt)
                    .tradeStatus(TradeStatus.OPEN)
                    .build();
            account.addTrade(trade);
        }
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
        Optional<AccountBalance> existing = account.getBalances().stream()
                .filter(b -> asset.equals(b.getAsset()))
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setAmount(existing.get().getAmount().subtract(amount));
        }
    }
}
