package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.TradeRequest;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import org.springframework.data.repository.query.ListQueryByExampleExecutor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TradingService {
    Trade openTrade (TradeRequest tradeRequest);
    Trade closeTrade (UUID tradeId,BigDecimal exitPrice);
    Trade partialClose (UUID tradeId,BigDecimal quantity,BigDecimal exitPrice);
    Trade getTradeById(UUID tradeId);
    List<Trade>getTradesByFilters(UUID accountId,TradeType type,String symbol);
    Trade updateStopLoss(UUID tradeId,BigDecimal stopLoss);
    Trade updateTakeProfit(UUID tradeId,BigDecimal takeProfit);



}
