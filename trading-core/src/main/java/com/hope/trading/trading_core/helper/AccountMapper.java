package com.hope.trading.trading_core.helper;

import com.hope.trading.trading_core.dto.AccountBalanceDto;
import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.dto.BrokerAccountDto;
import com.hope.trading.trading_core.model.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AccountMapper {



        public AccountDto toDto(Account account) {

            return AccountDto.builder()
                    .accountId(account.getAccountId())
                    .name(account.getName())
                    .baseCurrency(account.getBaseCurrency())
                    .equity(account.getEquity())
                    .peakEquity(account.getPeakEquity())
                    .rulesId(
                            account.getRules() != null
                                    ? account.getRules().getRulesId()
                                    : null
                    )
                    .userId(
                            account.getUser() != null
                                    ? account.getUser().getUserId()
                                    : null
                    )
                    .balances(
                            toAccountBalanceDto(account.getBalances())


                    )

                    .build();
        }







        public Account toEntity(BrokerAccountDto brokerAccountDto) {

            return Account.builder()
                    .broker(brokerAccountDto.getBroker())
                    .baseCurrency(brokerAccountDto.getBaseCurrency())
                    .name(brokerAccountDto.getAccountName())
                    .balances(toAccountBalance(brokerAccountDto.getBalances()))
                    .build();
        }

        public List<AccountBalance> toAccountBalance(AccountBalanceDto accountBalanceDto){
                return accountBalanceDto.getBalances()
                        .entrySet()
                        .stream()
                        .map(entry -> {
                                AccountBalance accountBalance = new AccountBalance();
                                accountBalance.setAsset(entry.getKey());
                                accountBalance.setAmount(entry.getValue());
                                return accountBalance;
                        })
                        .toList();
        }

    public void updateEntity(Account account, BrokerAccountDto snapshot) {

        account.setBroker(snapshot.getBroker());
        account.setName(snapshot.getAccountName());
        account.setBaseCurrency(snapshot.getBaseCurrency());
    }

    public AccountBalanceDto toAccountBalanceDto(List<AccountBalance> accountBalances) {

        return AccountBalanceDto.builder()
                .balances(
                        accountBalances.stream()
                                .collect(Collectors.toMap(
                                        AccountBalance::getAsset,
                                        AccountBalance::getAmount
                                ))
                )
                .build();
    }
        }



