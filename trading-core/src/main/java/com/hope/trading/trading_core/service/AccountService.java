package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.model.Account;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AccountService {
    Account getAccountById(UUID accountId,String username);


    BigDecimal getTotalBalance(UUID accountId,String username);

    BigDecimal getEquity(UUID accountId,String username);

    void updateEquity(UUID accountId, BigDecimal pnl,String username);

    BigDecimal getCurrentDrawdown(UUID accountId,String username);


    List<AccountDto> getAllUserAccounts(String username);
    BigDecimal getAvailableBalance(
            UUID accountId,
            String asset,
            String username
    );



}
