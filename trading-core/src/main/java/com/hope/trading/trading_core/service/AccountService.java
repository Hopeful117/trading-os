package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.dto.BrokerAccountDto;
import com.hope.trading.trading_core.model.Account;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AccountService {
    Account getAccountById(UUID accountId);


    BigDecimal getTotalBalance(UUID accountId);

    BigDecimal getEquity(UUID accountId);

    void updateEquity(UUID accountId, BigDecimal pnl);

    BigDecimal getCurrentDrawdown(UUID accountId);

    void createAccount(BrokerAccountDto brokerAccountDto,String username);

    List<AccountDto> getAllUserAccounts(String username);
    BigDecimal getAvailableBalance(
            UUID accountId,
            String asset
    );



}
