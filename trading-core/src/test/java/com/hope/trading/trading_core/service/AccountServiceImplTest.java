package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.exception.EntityNotFoundException;
import com.hope.trading.trading_core.helper.AccountMapper;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * STORY-0020A-2: protects account-state semantics — ownership scoping by
 * username, equity/peak tracking after PnL updates, drawdown derivation and
 * per-asset available balance. These numbers gate the risk engine.
 */
class AccountServiceImplTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private AccountServiceImpl service;

    private final UUID accountId = UUID.randomUUID();
    private final String username = "trader";
    private Account account;

    @BeforeEach
    void setUp() {
        service = new AccountServiceImpl(accountRepository, accountMapper, userRepository);
        account = new Account();
        account.setAccountId(accountId);
        account.setEquity(new BigDecimal("1000"));
        account.setPeakEquity(new BigDecimal("1200"));
        account.setBalances(List.of(
                balance("BTC", "500"),
                balance("EUR", "300")));
    }

    private static AccountBalance balance(String asset, String amount) {
        AccountBalance balance = new AccountBalance();
        balance.setAsset(asset);
        balance.setAmount(new BigDecimal(amount));
        return balance;
    }

    private void ownedByAuthenticatedUser() {
        when(accountRepository.findByAccountIdAndUser_Username(accountId, username))
                .thenReturn(Optional.of(account));
    }

    @Test
    void getAccountByIdIsScopedToTheOwner() {
        ownedByAuthenticatedUser();
        assertThat(service.getAccountById(accountId, username)).isSameAs(account);
    }

    @Test
    void accountOfAnotherUserIsInvisible() {
        when(accountRepository.findByAccountIdAndUser_Username(accountId, "other"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAccountById(accountId, "other"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("account not found");
    }

    @Test
    void totalBalanceSumsAllAssets() {
        ownedByAuthenticatedUser();
        assertThat(service.getTotalBalance(accountId, username)).isEqualByComparingTo("800");
    }

    @Test
    void availableBalanceMatchesAssetCaseInsensitivelyAndDefaultsToZero() {
        ownedByAuthenticatedUser();
        assertThat(service.getAvailableBalance(accountId, "btc", username))
                .isEqualByComparingTo("500");
        assertThat(service.getAvailableBalance(accountId, "USD", username))
                .isEqualByComparingTo("0");
    }

    @Test
    void updateEquityTracksPeakEquityMonotonically() {
        ownedByAuthenticatedUser();

        // Loss: equity drops, peak stays.
        service.updateEquity(accountId, new BigDecimal("-200"), username);
        assertThat(account.getEquity()).isEqualByComparingTo("800");
        assertThat(account.getPeakEquity()).isEqualByComparingTo("1200");

        // Gain beyond previous peak: peak moves up.
        service.updateEquity(accountId, new BigDecimal("600"), username);
        assertThat(account.getEquity()).isEqualByComparingTo("1400");
        assertThat(account.getPeakEquity()).isEqualByComparingTo("1400");
    }

    @Test
    void drawdownIsPeakMinusCurrentEquity() {
        ownedByAuthenticatedUser();
        assertThat(service.getCurrentDrawdown(accountId, username))
                .isEqualByComparingTo("200");
    }
}
