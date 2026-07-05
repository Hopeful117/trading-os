package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.TradeRequest;

public interface TradeRequestValidator {
    void validate(TradeRequest request);
}
