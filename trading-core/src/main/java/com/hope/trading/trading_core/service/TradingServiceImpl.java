package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.TradeCalculation;
import com.hope.trading.trading_core.dto.TradeRequest;
import com.hope.trading.trading_core.exception.BrokenRulesException;
import com.hope.trading.trading_core.exception.EntityNotFoundException;
import com.hope.trading.trading_core.helper.RiskResult;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.TradeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TradingServiceImpl implements TradingService {
    private final TradeRepository tradeRepository;
    private final AccountService accountService;
    private final RiskEngine riskEngine;
    private final TradingCalculatorService tradingCalculatorService;
    private final TradeRequestValidator validator;


    @Override
    @Transactional
    public Trade openTrade(TradeRequest tradeRequest) {
        Account account = accountService.getAccountById(tradeRequest.getAccountId());
        validator.validate(tradeRequest);

        // 2. CALCULS
        TradeCalculation calc = tradingCalculatorService.calculate(tradeRequest, account.getBalance());

        // 3. RISK CHECK
        RiskResult result = riskEngine.assertTradeAllowed(account, account.getRules(), tradeRequest);

        if (!result.isAllowed()) {
            throw new BrokenRulesException(result.getMessage());
        }

        // 4. DOMAIN OBJECT
        Trade trade = Trade.builder()


                .symbol(tradeRequest.getSymbol())
                .type(tradeRequest.getType())

                .entryPrice(tradeRequest.getEntryPrice())
                .exitPrice(null) // trade ouvert au début

                .quantity(tradeRequest.getQuantity())

                .pnl(null) // calculé à la fermeture

                .openedAt(Instant.now())
                .closedAt(null)

                .stopLoss(tradeRequest.getStopLoss())
                .takeProfit(tradeRequest.getTakeProfit())

                .riskAmount(calc.getRiskAmount())
                .rewardAmount(calc.getRewardAmount())
                .riskRewardRatio(calc.getRiskRewardRatio())

                .account(account)
                .build();

        // 5. PERSISTENCE
        return tradeRepository.save(trade);
    }

    @Override
    @Transactional
    public Trade closeTrade(UUID tradeId, BigDecimal exitPrice) {
        // 1. load trade
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Trade not found with id: " + tradeId));

        // 2. safety check
        if (trade.getClosedAt() != null) {
            throw new IllegalStateException("Trade already closed");
        }

        // 3. update trade state
        trade.setExitPrice(exitPrice);
        trade.setClosedAt(Instant.now());

        // 4. calculate pnl (clean + stateless)
        BigDecimal pnl = tradingCalculatorService.calculatePnL(
                trade.getType(),
                trade.getEntryPrice(),
                exitPrice,
                trade.getQuantity()
        );

        trade.setPnl(pnl);

        // 5. update account equity
        Account account = trade.getAccount();

        account.setEquity(
                account.getEquity().add(pnl)
        );

        accountService.updateEquity(account.getAccountId(), pnl);

        // 6. persist trade
        return tradeRepository.save(trade);


    }

    @Override
    @Transactional
    public Trade partialClose(UUID tradeId, BigDecimal quantity, BigDecimal exitPrice) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Trade not found with id: " + tradeId));

        if (trade.getClosedAt() != null) {
            throw new IllegalStateException("Trade already closed");
        }

        if (quantity.compareTo(trade.getQuantity()) > 0) {
            throw new IllegalArgumentException("Partial close quantity exceeds open trade quantity");
        }

        // Calculate PnL for the partial close
        BigDecimal pnl = tradingCalculatorService.calculatePnL(
                trade.getType(),
                trade.getEntryPrice(),
                exitPrice,
                quantity
        );

        // Update the trade's quantity and PnL
        trade.setQuantity(trade.getQuantity().subtract(quantity));
        trade.setPnl(trade.getPnl() == null ? pnl : trade.getPnl().add(pnl));

        // If the remaining quantity is zero, mark the trade as closed
        if (trade.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            trade.setClosedAt(Instant.now());
            trade.setExitPrice(exitPrice);
        }

        // Update account equity
        Account account = trade.getAccount();
        account.setEquity(account.getEquity().add(pnl));
        accountService.updateEquity(account.getAccountId(), pnl);

        return tradeRepository.save(trade);
    }

    @Override
    public Trade getTradeById(UUID tradeId){
        return tradeRepository.findById(tradeId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Trade not found with id: " + tradeId));
    }

    @Override
    public List<Trade> getTradesByFilters(UUID accountId, TradeType type, String symbol) {
        return tradeRepository.findAllByAccountId(accountId).stream().filter(trade -> {
            boolean matchesType = type == null || trade.getType() == type;
            boolean matchesSymbol = symbol == null || trade.getSymbol().equals(symbol);
            return matchesType && matchesSymbol;
        }).toList();
    }

    @Override
    @Transactional
    public Trade updateStopLoss(UUID tradeId, BigDecimal stopLoss) {
        return tradeRepository.findById(tradeId)
                .map(trade -> {
                    trade.setStopLoss(stopLoss);
                    return tradeRepository.save(trade);
                })
                .orElseThrow(() ->
                        new EntityNotFoundException("Trade not found with id: " + tradeId));
    }

    @Override
    @Transactional
    public Trade updateTakeProfit(UUID tradeId, BigDecimal takeProfit) {
        return tradeRepository.findById(tradeId)
                .map(trade -> {
                    trade.setTakeProfit(takeProfit);
                    return tradeRepository.save(trade);
                })
                .orElseThrow(() ->
                        new EntityNotFoundException("Trade not found with id: " + tradeId));
    }


}
