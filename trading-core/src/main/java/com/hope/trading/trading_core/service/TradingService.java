package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.TradeDto;
import com.hope.trading.trading_core.dto.TradeRequest;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Account;

import org.springframework.data.repository.query.ListQueryByExampleExecutor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TradingService {
    TradeDto openTrade (TradeRequest tradeRequest, UUID actorId);
    TradeDto closeTrade (UUID tradeId,BigDecimal exitPrice, UUID actorId);
    TradeDto partialClose (UUID tradeId,BigDecimal quantity,BigDecimal exitPrice, UUID actorId);
    TradeDto getTradeById(UUID tradeId, UUID actorId);
    List<TradeDto>getTradesByFilters(UUID accountId,TradeType type,String symbol, UUID actorId);
    TradeDto updateStopLoss(UUID tradeId, BigDecimal stopLoss, UUID actorId);
    TradeDto updateTakeProfit(UUID tradeId,BigDecimal takeProfit, UUID actorId);



}
