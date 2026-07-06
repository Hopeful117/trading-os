package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.dto.AccountRequest;
import com.hope.trading.trading_core.exception.EntityNotFoundException;
import com.hope.trading.trading_core.helper.AccountMapper;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Rules;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.RulesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final RulesRepository rulesRepository;

    @Override
    public AccountDto getAccountById(UUID accountId) {
        return accountRepository.findById(accountId).map(accountMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));
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
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));
        account.setEquity(account.getEquity().add(pnl));
        accountRepository.save(account);
    }



    @Override
    public BigDecimal getCurrentDrawdown(UUID accountId) {
        return getAccountById(accountId).getBalance().subtract(getAccountById(accountId).getEquity());
    }

    @Override
    public AccountDto createAccount(AccountRequest accountRequest) {
        Account account = accountMapper.toEntity(accountRequest);
        account.setBalance(
                accountRequest.getBalance() != null ? accountRequest.getBalance() : BigDecimal.ZERO
        );


        if (accountRequest.getRulesId() != null) {
            Rules rules = rulesRepository.findById(accountRequest.getRulesId())
                    .orElseThrow(() -> new RuntimeException("Rules not found"));
            account.setRules(rules);
        }

        if (accountRequest.getUserId() != null) {
            User user = User.builder().userId(accountRequest.getUserId()).build();
            account.setUser(user);
        }

        account.setEquity(account.getBalance());
        return accountMapper.toDto(accountRepository.save(account));
    }

    @Override
    public List<AccountDto> getAllAccounts(){
        return accountRepository.findAll().stream().map(accountMapper::toDto).toList();
    }

}



