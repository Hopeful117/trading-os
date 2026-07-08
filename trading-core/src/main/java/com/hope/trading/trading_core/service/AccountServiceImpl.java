package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.AccountBalanceDto;
import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.dto.AccountRequest;
import com.hope.trading.trading_core.exception.EntityNotFoundException;
import com.hope.trading.trading_core.helper.AccountMapper;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.Rules;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.RulesRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountServiceImpl implements AccountService {


    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final RulesRepository rulesRepository;
    private final UserRepository userRepository;


    @Override
    @Transactional(readOnly = true)
    public AccountDto getAccountById(UUID accountId) {

        return accountMapper.toDto(findAccount(accountId));
    }


    @Override
    @Transactional(readOnly = true)
    public List<AccountBalanceDto> getAllBalances(UUID accountId) {

        return findAccount(accountId)
                .getBalances()
                .stream()
                .map(accountMapper::toBalanceDto)
                .toList();
    }


    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalBalance(UUID accountId) {

        return findAccount(accountId)
                .getBalances()
                .stream()
                .map(AccountBalance::getAmount)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );
    }


    @Override
    @Transactional(readOnly = true)
    public BigDecimal getEquity(UUID accountId) {

        return findAccount(accountId)
                .getEquity();
    }


    @Override
    public void updateEquity(UUID accountId, BigDecimal pnl) {

        Account account = findAccount(accountId);


        BigDecimal newEquity =
                account.getEquity()
                        .add(pnl);


        account.setEquity(newEquity);


        if (newEquity.compareTo(account.getPeakEquity()) > 0) {

            account.setPeakEquity(newEquity);

        }


        accountRepository.save(account);
    }


    @Override
    @Transactional(readOnly = true)
    public BigDecimal getCurrentDrawdown(UUID accountId) {

        Account account = findAccount(accountId);


        return account.getPeakEquity()
                .subtract(account.getEquity());
    }


    @Override
    public AccountDto createAccount(AccountRequest accountRequest) {

        Account account = accountMapper.toEntity(accountRequest);


        if (accountRequest.getRulesId() != null) {

            Rules rules = rulesRepository.findById(accountRequest.getRulesId())
                    .orElseThrow(() ->
                            new EntityNotFoundException(
                                    "Rules not found with id: "
                                            + accountRequest.getRulesId()
                            )
                    );

            account.setRules(rules);
        }


        if (accountRequest.getUserId() != null) {

            User user = userRepository.findById(accountRequest.getUserId())
                    .orElseThrow(() ->
                            new EntityNotFoundException(
                                    "User not found with id: "
                                            + accountRequest.getUserId()
                            )
                    );


            account.setUser(user);

            user.addAccount(account);
        }


        BigDecimal initialEquity =
                accountRequest.getInitialEquity() != null
                        ? accountRequest.getInitialEquity()
                        : BigDecimal.ZERO;


        account.setEquity(initialEquity);

        account.setPeakEquity(initialEquity);


        return accountMapper.toDto(
                accountRepository.save(account)
        );
    }


    @Override
    @Transactional(readOnly = true)
    public List<AccountDto> getAllAccounts() {

        return accountRepository.findAll()
                .stream()
                .map(accountMapper::toDto)
                .toList();
    }

    @Override
    public BigDecimal getAvailableBalance(UUID accountId, String asset) {
        Account account = findAccount(accountId);

        return account.getBalances()
                .stream()
                .filter(balance ->
                        balance.getAsset().equalsIgnoreCase(asset)
                )
                .map(AccountBalance::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }


    private Account findAccount(UUID accountId) {

        return accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Account not found with id: "
                                        + accountId
                        )
                );
    }
}