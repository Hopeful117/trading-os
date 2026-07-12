package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.dto.TradeCalculation;
import com.hope.trading.trading_core.dto.TradeDto;
import com.hope.trading.trading_core.dto.TradeRequest;
import com.hope.trading.trading_core.exception.BrokenRulesException;
import com.hope.trading.trading_core.exception.EntityNotFoundException;
import com.hope.trading.trading_core.helper.*;
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
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final RiskEngine riskEngine;
    private final TradingCalculatorService tradingCalculatorService;
    private final TradeRequestValidator validator;
    private final TradeMapper tradeMapper;




    @Override
    @Transactional
    public TradeDto openTrade(TradeRequest tradeRequest,String username) {
        Account account = accountRepository.findById(tradeRequest.getAccountId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Account not found with id: " + tradeRequest.getAccountId()));
        validator.validate(tradeRequest);
        BigDecimal availableFunds =
                accountService.getAvailableBalance(
                        account.getAccountId(),
                        tradeRequest.getQuoteAsset(),
                        username
                );
        BigDecimal entryPrice = null;
        //remplacer par brokerService.getCurrentPrice(tradeRequest.getSymbol());

        // 2. CALCULS
        TradeCalculation calc = tradingCalculatorService.calculate(tradeRequest,entryPrice,availableFunds);

        // 3. RISK CHECK
        RiskResult result = riskEngine.assertTradeAllowed(account, account.getRules(), tradeRequest,entryPrice,availableFunds);

        if (!result.isAllowed()) {
            throw new BrokenRulesException(result.getMessage());
        }

        // 4. DOMAIN OBJECT
        Trade trade = tradeMapper.toEntity(tradeRequest,calc);
        trade.setAccount(account);
        trade.setOpenedAt(Instant.now());
        trade.setTradeStatus(TradeStatus.OPEN);
        trade.setCurrentPrice(entryPrice);

        return tradeMapper.toDto(tradeRepository.save(trade));

    }

    @Override
    @Transactional
    public TradeDto closeTrade(UUID tradeId, BigDecimal exitPrice,String username) {
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
        trade.setCurrentPrice(exitPrice);
        trade.setTradeStatus(TradeStatus.CLOSED);

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

        accountService.updateEquity(account.getAccountId(), pnl,username);

        // 6. persist trade
      return tradeMapper.toDto(tradeRepository.save(trade));


    }

    @Override
    @Transactional
    public TradeDto partialClose(UUID tradeId, BigDecimal quantity, BigDecimal exitPrice,String username) {
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
        trade.setCurrentPrice(exitPrice);

        // If the remaining quantity is zero, mark the trade as closed
        if (trade.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            trade.setClosedAt(Instant.now());
            trade.setExitPrice(exitPrice);
            trade.setTradeStatus(TradeStatus.CLOSED);
        }

        // Update account equity
        Account account = trade.getAccount();
        account.setEquity(account.getEquity().add(pnl));
        accountService.updateEquity(account.getAccountId(), pnl,username);

        return tradeMapper.toDto(tradeRepository.save(trade));
    }

    @Override
    public TradeDto getTradeById(UUID tradeId) {
        return tradeRepository.findById(tradeId)
                .map(tradeMapper::toDto)
                .orElseThrow(() ->
                        new EntityNotFoundException("Trade not found with id: " + tradeId));
    }

    @Override
    public List<TradeDto> getTradesByFilters(UUID accountId, TradeType type, String symbol) {
        return tradeRepository.findAllByAccount_AccountId(accountId).stream().filter(trade -> {
            boolean matchesType = type == null || trade.getType() == type;
            boolean matchesSymbol = symbol == null || trade.getSymbol().equals(symbol);
            return matchesType && matchesSymbol;
        }).map(tradeMapper::toDto).toList();
    }

    @Override
    @Transactional
    public TradeDto updateStopLoss(UUID tradeId, BigDecimal stopLoss) {
        return tradeRepository.findById(tradeId)
                .map(trade -> {
                    trade.setStopLoss(stopLoss);
                    return tradeMapper.toDto(tradeRepository.save(trade));
                })
                .orElseThrow(() ->
                        new EntityNotFoundException("Trade not found with id: " + tradeId));
    }

    @Override
    @Transactional
    public TradeDto updateTakeProfit(UUID tradeId, BigDecimal takeProfit) {
        return tradeRepository.findById(tradeId)
                .map(trade -> {
                    trade.setTakeProfit(takeProfit);
                    return tradeMapper.toDto(tradeRepository.save(trade));
                })
                .orElseThrow(() ->
                        new EntityNotFoundException("Trade not found with id: " + tradeId));
    }


}
