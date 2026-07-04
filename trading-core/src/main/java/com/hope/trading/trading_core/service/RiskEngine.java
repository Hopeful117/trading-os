package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.TradeRequest;
import com.hope.trading.trading_core.helper.RiskResult;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Rules;

import java.math.BigDecimal;

public interface RiskEngine {
    RiskResult assertTradeAllowed(Account account, Rules rules, TradeRequest tradeRequest);
}
