package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.TradeRequest;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;

import java.math.BigDecimal;
import java.util.UUID;

public interface TradingService {
    Trade openTrade (TradeRequest tradeRequest);
    Trade closeTrade (UUID tradeId,BigDecimal exitPrice);


}
