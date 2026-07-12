package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.dto.BrokerAccountDto;
import com.hope.trading.trading_core.exception.DuplicateRessourceException;
import com.hope.trading.trading_core.exception.EntityNotFoundException;
import com.hope.trading.trading_core.helper.AccountMapper;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
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
    private final UserRepository userRepository;


    @Override
    public Account getAccountById(UUID accountId) {
       return accountRepository.findById(accountId).orElseThrow(()->new EntityNotFoundException("account not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalBalance(UUID accountId) {

        return getAccountById(accountId)
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

        return getAccountById(accountId)
                .getEquity();
    }


    @Override
    public void updateEquity(UUID accountId, BigDecimal pnl) {

        Account account = getAccountById(accountId);


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

        Account account = getAccountById(accountId);


        return account.getPeakEquity()
                .subtract(account.getEquity());
    }


    @Override
    @Transactional
    public void createAccount(BrokerAccountDto brokerAccountDto,String username) {
        User user=userRepository.findByUsername(username).orElseThrow(()->new EntityNotFoundException("User not found"));
        if(accountRepository.existByUser_UserIdAndBroker(user.getUserId(), brokerAccountDto.getBroker())){
            throw new DuplicateRessourceException("Account already exists");
        }

        Account account = accountMapper.toEntity(brokerAccountDto);

            account.setUser(user);

            user.addAccount(account);
            accountRepository.save(account);
        }












    @Override
    @Transactional(readOnly = true)
    public List<AccountDto> getAllUserAccounts(String username) {
        User user = userRepository.findByUsername(username).orElseThrow(()->new EntityNotFoundException("User not found"));

        return accountRepository.findAllByUser_UserId(user.getUserId())
                .stream()
                .map(accountMapper::toDto)
                .toList();
    }

    @Override
    public BigDecimal getAvailableBalance(UUID accountId, String asset) {
        Account account = getAccountById(accountId);

        return account.getBalances()
                .stream()
                .filter(balance ->
                        balance.getAsset().equalsIgnoreCase(asset)
                )
                .map(AccountBalance::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }



}