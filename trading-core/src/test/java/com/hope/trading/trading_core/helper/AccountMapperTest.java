package com.hope.trading.trading_core.helper;

import com.hope.trading.trading_core.broker.dto.AccountBalanceDto;
import com.hope.trading.trading_core.broker.dto.BrokerAccountDto;
import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.Rules;
import com.hope.trading.trading_core.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountMapperTest {

    private final AccountMapper mapper = new AccountMapper();

    @Test
    void toDtoMapsAllFieldsIncludingNestedIds() {
        UUID accountId = UUID.randomUUID();
        UUID rulesId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Rules rules = new Rules();
        rules.setRulesId(rulesId);

        User user = new User();
        user.setUserId(userId);

        AccountBalance balance = new AccountBalance();
        balance.setAsset("BTC");
        balance.setAmount(new BigDecimal("1.5"));

        Account account = new Account();
        account.setAccountId(accountId);
        account.setName("main-account");
        account.setBaseCurrency("USD");
        account.setEquity(new BigDecimal("10000"));
        account.setPeakEquity(new BigDecimal("12000"));
        account.setRules(rules);
        account.setUser(user);
        account.setBalances(List.of(balance));

        AccountDto dto = mapper.toDto(account);

        assertThat(dto.getAccountId()).isEqualTo(accountId);
        assertThat(dto.getName()).isEqualTo("main-account");
        assertThat(dto.getBaseCurrency()).isEqualTo("USD");
        assertThat(dto.getEquity()).isEqualByComparingTo("10000");
        assertThat(dto.getPeakEquity()).isEqualByComparingTo("12000");
        assertThat(dto.getRulesId()).isEqualTo(rulesId);
        assertThat(dto.getUserId()).isEqualTo(userId);
        assertThat(dto.getBalances().getBalances()).containsEntry("BTC", new BigDecimal("1.5"));
    }

    @Test
    void toDtoHandlesNullRulesAndUser() {
        Account account = new Account();
        account.setName("orphan");
        account.setBaseCurrency("EUR");

        AccountDto dto = mapper.toDto(account);

        assertThat(dto.getRulesId()).isNull();
        assertThat(dto.getUserId()).isNull();
        assertThat(dto.getName()).isEqualTo("orphan");
    }

    @Test
    void toEntityMapsBrokerAccountDtoFields() {
        AccountBalanceDto balanceDto = AccountBalanceDto.builder()
                .balances(Map.of(
                        "BTC", new BigDecimal("2.0"),
                        "ETH", new BigDecimal("10.0")))
                .build();

        BrokerAccountDto brokerDto = BrokerAccountDto.builder()
                .broker("kraken")
                .baseCurrency("USD")
                .accountName("prop-firm-1")
                .balances(balanceDto)
                .build();

        Account account = mapper.toEntity(brokerDto);

        assertThat(account.getBroker()).isEqualTo("kraken");
        assertThat(account.getBaseCurrency()).isEqualTo("USD");
        assertThat(account.getName()).isEqualTo("prop-firm-1");
        assertThat(account.getBalances()).hasSize(2);
    }

    @Test
    void toAccountBalanceConvertsMapEntriesToEntityList() {
        AccountBalanceDto balanceDto = AccountBalanceDto.builder()
                .balances(Map.of(
                        "BTC", new BigDecimal("1.0"),
                        "EUR", new BigDecimal("5000"),
                        "USD", new BigDecimal("10000")))
                .build();

        List<AccountBalance> balances = mapper.toAccountBalance(balanceDto);

        assertThat(balances).hasSize(3);
        assertThat(balances).extracting(AccountBalance::getAsset)
                .containsExactlyInAnyOrder("BTC", "EUR", "USD");
        assertThat(balances).extracting(AccountBalance::getAmount)
                .containsExactlyInAnyOrder(
                        new BigDecimal("1.0"),
                        new BigDecimal("5000"),
                        new BigDecimal("10000"));
    }

    @Test
    void toAccountBalanceDtoConvertsEntityListToMap() {
        AccountBalance b1 = new AccountBalance();
        b1.setAsset("BTC");
        b1.setAmount(new BigDecimal("0.5"));

        AccountBalance b2 = new AccountBalance();
        b2.setAsset("ETH");
        b2.setAmount(new BigDecimal("3.0"));

        AccountBalanceDto dto = mapper.toAccountBalanceDto(List.of(b1, b2));

        assertThat(dto.getBalances()).containsEntry("BTC", new BigDecimal("0.5"));
        assertThat(dto.getBalances()).containsEntry("ETH", new BigDecimal("3.0"));
    }
}
