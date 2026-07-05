package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.model.Account;

import java.math.BigDecimal;
import java.util.UUID;

public interface AccountService {
    Account getAccountById(UUID accountId);
    BigDecimal getBalance(UUID accountId);

    BigDecimal getEquity(UUID accountId);

    void updateEquity(UUID accountId, BigDecimal pnl);

    BigDecimal getCurrentDrawdown(UUID accountId);



}
