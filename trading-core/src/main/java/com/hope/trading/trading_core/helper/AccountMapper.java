package com.hope.trading.trading_core.helper;

import com.hope.trading.trading_core.dto.AccountBalanceDto;
import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.dto.AccountRequest;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.Rules;
import com.hope.trading.trading_core.model.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
                            account.getBalances()
                                    .stream()
                                    .map(this::toBalanceDto)
                                    .toList()
                    )
                    .build();
        }


        public AccountBalanceDto toBalanceDto(AccountBalance balance) {


            return AccountBalanceDto.builder()
                    .asset(balance.getAsset())
                    .amount(balance.getAmount())
                    .build();
        }


        public Account toEntity(AccountRequest accountRequest) {

            return Account.builder()
                    .name(accountRequest.getName())
                    .baseCurrency(accountRequest.getBaseCurrency())
                    .rules(
                            accountRequest.getRulesId() != null
                                    ? Rules.builder()
                                    .rulesId(accountRequest.getRulesId())
                                    .build()
                                    : null
                    )
                    .equity(
                            accountRequest.getInitialEquity() != null
                                    ? accountRequest.getInitialEquity()
                                    : BigDecimal.ZERO
                    )
                    .peakEquity(
                            accountRequest.getInitialEquity() != null
                                    ? accountRequest.getInitialEquity()
                                    : BigDecimal.ZERO
                    )
                    .user(
                            accountRequest.getUserId() != null
                                    ? User.builder()
                                    .userId(accountRequest.getUserId())
                                    .build()
                                    : null
                    )
                    .build();
        }
}
