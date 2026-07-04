package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.TradeRequest;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TradingServiceImpl implements TradingService {
    private final TradeRepository tradeRepository;
    private final AccountRepository accountRepository;
    private final RiskEngine riskEngine;



    @Override
    @Transactional
    public Trade openTrade(Account account, TradeRequest tradeRequest) {
        String symbol = tradeRequest.getSymbol();
        TradeType type = tradeRequest.getType();
        BigDecimal entryPrice = tradeRequest.getEntryPrice();
        BigDecimal quantity = tradeRequest.getQuantity();
        BigDecimal riskAmount = tradeRequest.getRiskAmount();
        BigDecimal todayPnL = tradeRequest.getTodayPnL();
        int tradesToday = tradeRequest.getTradesToday();

        RiskResult riskResult = riskEngine.assertTradeAllowed(account, account.getRules(),tradeRequest);
        if (!riskResult.isAllowed()) {
            throw new IllegalArgumentException("Trade not allowed: " + riskResult.getMessage());
        }

        Trade trade = new Trade();
        trade.setSymbol(symbol);
        trade.setType(type);
        trade.setEntryPrice(entryPrice);
        trade.setQuantity(quantity);
        trade.setOpenedAt(Instant.now());

        return tradeRepository.save(trade);
    }

    @Override
    @Transactional
    public Trade closeTrade(UUID tradeId, BigDecimal exitPrice) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new EntityNotFoundException("Trade not found with id: " + tradeId));

        trade.setExitPrice(exitPrice);
        trade.setClosedAt(Instant.now());

        BigDecimal pnl = calculatePnL(trade);
        trade.setPnl(pnl);

        Account account = trade.getAccount();
        account.setEquity(account.getEquity().add(pnl));

        accountRepository.save(account);

        return tradeRepository.save(trade);
    }

    private BigDecimal calculatePnL(Trade trade) {

        BigDecimal diff = trade.getExitPrice()
                .subtract(trade.getEntryPrice());

        if (trade.getType() == TradeType.SELL) {
            diff = diff.negate();
        }

        return diff.multiply(trade.getQuantity());
    }




}
