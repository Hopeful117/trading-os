package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.exception.EntityNotFoundException;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
    private final AccountRepository accountRepository;

    @Override
    public Account getAccountById(UUID accountId) {
        return accountRepository.findById(accountId).orElseThrow(()-> new EntityNotFoundException("Account not found with id: " + accountId));
    }

    @Override
    public BigDecimal getBalance(UUID accountId) {
        return getAccountById(accountId).getBalance();
    }

    @Override
    public BigDecimal getEquity(UUID accountId) {
        return getAccountById(accountId).getEquity();
    }

    @Override
    public void updateEquity(UUID accountId, BigDecimal pnl) {
        Account account = getAccountById(accountId);
        account.setEquity(account.getEquity().add(pnl));
        accountRepository.save(account);
    }



    @Override
    public BigDecimal getCurrentDrawdown(UUID accountId) {
        return getAccountById(accountId).getBalance().subtract(getAccountById(accountId).getEquity());
    }

    }



