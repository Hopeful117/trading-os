package com.hope.trading.trading_core.helper;

import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.dto.AccountRequest;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Rules;
import com.hope.trading.trading_core.model.User;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {
    public AccountDto toDto(Account account){
        return AccountDto.builder()
                .accountId(account.getAccountId())
                .userId(account.getUser().getUserId())
                .balance(account.getBalance())
                .build();
    }

    public Account toEntity(AccountRequest accountRequest){
        return Account.builder()

                .name(accountRequest.getName())
                .balance(accountRequest.getBalance())
                .equity(accountRequest.getEquity())
                .rules(accountRequest.getRulesId() != null ? Rules.builder().rulesId(accountRequest.getRulesId()).build() : null)
                .user(accountRequest.getUserId() != null ? User.builder().userId(accountRequest.getUserId()).build() : null)
                .build();
    }
}
