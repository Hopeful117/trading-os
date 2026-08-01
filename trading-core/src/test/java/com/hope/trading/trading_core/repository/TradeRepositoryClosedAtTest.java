package com.hope.trading.trading_core.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.model.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class TradeRepositoryClosedAtTest {
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired TradeRepository trades;

    @Test
    void selectsClosedAtUsingHalfOpenBoundariesRegardlessOfOpenedAt() {
        User user = users.save(User.builder().username("closed-query").password("x")
                .email("closed-query@test.local").role(Role.ROLE_USER).build());
        Account account = accounts.save(Account.builder().broker("closed-query-" + UUID.randomUUID())
                .name("account").baseCurrency("USD").user(user).build());
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        Trade included = trade(account, start.minusSeconds(3600), start);
        Trade excludedAtEnd = trade(account, start, start.plusSeconds(86400));
        trades.save(included); trades.save(excludedAtEnd);

        assertThat(trades.findByAccount_AccountIdAndClosedAtGreaterThanEqualAndClosedAtLessThan(
                account.getAccountId(), start, start.plusSeconds(86400)))
                .extracting(Trade::getTradeId).containsExactly(included.getTradeId());
    }

    private Trade trade(Account account, Instant openedAt, Instant closedAt) {
        return Trade.builder().symbol("ETHUSD").type(TradeType.BUY).entryPrice(BigDecimal.ONE)
                .quantity(BigDecimal.ONE).openedAt(openedAt).closedAt(closedAt).pnl(BigDecimal.ONE)
                .tradeStatus(TradeStatus.CLOSED).account(account).build();
    }
}
