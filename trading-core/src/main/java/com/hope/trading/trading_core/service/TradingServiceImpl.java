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
    public TradeDto openTrade(TradeRequest tradeRequest, UUID actorId) {
        Account account = accountRepository.findById(tradeRequest.getAccountId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Account not found with id: " + tradeRequest.getAccountId()));
        requireAccountOwner(account, actorId);
        validator.validate(tradeRequest);
        BigDecimal availableFunds =
                accountService.getAvailableBalance(
                        account.getAccountId(),
                        tradeRequest.getQuoteAsset(),
                        account.getUser().getUsername()
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
    public TradeDto closeTrade(UUID tradeId, BigDecimal exitPrice, UUID actorId) {
        // 1. load trade
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Trade not found with id: " + tradeId));
        requireTradeOwner(trade, actorId);

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

        accountService.updateEquity(account.getAccountId(), pnl, account.getUser().getUsername());

        // 6. persist trade
      return tradeMapper.toDto(tradeRepository.save(trade));


    }

    @Override
    @Transactional
    public TradeDto partialClose(UUID tradeId, BigDecimal quantity, BigDecimal exitPrice, UUID actorId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Trade not found with id: " + tradeId));
        requireTradeOwner(trade, actorId);

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
        accountService.updateEquity(account.getAccountId(), pnl, account.getUser().getUsername());

        return tradeMapper.toDto(tradeRepository.save(trade));
    }

    @Override
    public TradeDto getTradeById(UUID tradeId, UUID actorId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new EntityNotFoundException("Trade not found with id: " + tradeId));
        requireTradeOwner(trade, actorId);
        return tradeMapper.toDto(trade);
    }

    @Override
    public List<TradeDto> getTradesByFilters(UUID accountId, TradeType type, String symbol, UUID actorId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));
        requireAccountOwner(account, actorId);
        return tradeRepository.findAllByAccount_AccountId(accountId).stream().filter(trade -> {
            boolean matchesType = type == null || trade.getType() == type;
            boolean matchesSymbol = symbol == null || trade.getSymbol().equals(symbol);
            return matchesType && matchesSymbol;
        }).map(tradeMapper::toDto).toList();
    }

    @Override
    @Transactional
    public TradeDto updateStopLoss(UUID tradeId, BigDecimal stopLoss, UUID actorId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new EntityNotFoundException("Trade not found with id: " + tradeId));
        requireTradeOwner(trade, actorId);
        trade.setStopLoss(stopLoss);
        return tradeMapper.toDto(tradeRepository.save(trade));
    }

    @Override
    @Transactional
    public TradeDto updateTakeProfit(UUID tradeId, BigDecimal takeProfit, UUID actorId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new EntityNotFoundException("Trade not found with id: " + tradeId));
        requireTradeOwner(trade, actorId);
        trade.setTakeProfit(takeProfit);
        return tradeMapper.toDto(tradeRepository.save(trade));
    }

    private void requireTradeOwner(Trade trade, UUID actorId) {
        if (trade.getAccount() == null || trade.getAccount().getUser() == null
                || !actorId.equals(trade.getAccount().getUser().getUserId())) {
            throw new EntityNotFoundException("Trade not found with id: " + trade.getTradeId());
        }
    }

    private void requireAccountOwner(Account account, UUID actorId) {
        if (account.getUser() == null || !actorId.equals(account.getUser().getUserId())) {
            throw new EntityNotFoundException("Account not found with id: " + account.getAccountId());
        }
    }


}
