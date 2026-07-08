package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.AccountBalanceDto;
import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.dto.AccountRequest;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Rules;
import com.hope.trading.trading_core.model.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AccountService {
    AccountDto getAccountById(UUID accountId);
    List<AccountBalanceDto> getAllBalances(UUID accountId);

    BigDecimal getTotalBalance(UUID accountId);

    BigDecimal getEquity(UUID accountId);

    void updateEquity(UUID accountId, BigDecimal pnl);

    BigDecimal getCurrentDrawdown(UUID accountId);

    AccountDto createAccount(AccountRequest accountRequest);

    List<AccountDto> getAllAccounts();
    BigDecimal getAvailableBalance(
            UUID accountId,
            String asset
    );



}
