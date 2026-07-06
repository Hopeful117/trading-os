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
    TradeDto openTrade (TradeRequest tradeRequest);
    TradeDto closeTrade (UUID tradeId,BigDecimal exitPrice);
    TradeDto partialClose (UUID tradeId,BigDecimal quantity,BigDecimal exitPrice);
    TradeDto getTradeById(UUID tradeId);
    List<TradeDto>getTradesByFilters(UUID accountId,TradeType type,String symbol);
    TradeDto updateStopLoss(UUID tradeId, BigDecimal stopLoss);
    TradeDto updateTakeProfit(UUID tradeId,BigDecimal takeProfit);



}
