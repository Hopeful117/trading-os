package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.TradeRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TradingOwnershipPersistenceIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private AccountRepository accounts;
    @Autowired private TradeRepository trades;

    private User owner;
    private User otherUser;
    private Account ownerAccount;
    private Trade ownerTrade;

    @BeforeEach
    void setUp() {
        owner = users.save(User.builder().username("owner-" + UUID.randomUUID())
                .password("password").email(UUID.randomUUID() + "@test.local")
                .role(Role.ROLE_USER).build());
        otherUser = users.save(User.builder().username("other-" + UUID.randomUUID())
                .password("password").email(UUID.randomUUID() + "@test.local")
                .role(Role.ROLE_USER).build());
        ownerAccount = accounts.save(Account.builder()
                .brokerAccountId(UUID.randomUUID()).broker("KRAKEN").name("owner")
                .baseCurrency("USD").equity(new BigDecimal("10000"))
                .peakEquity(new BigDecimal("10000")).user(owner).build());
        ownerTrade = trades.save(Trade.builder().symbol("BTC/USD").type(TradeType.BUY)
                .entryPrice(new BigDecimal("100")).quantity(new BigDecimal("2"))
                .openedAt(Instant.parse("2026-09-15T10:00:00Z"))
                .tradeStatus(TradeStatus.OPEN).stopLoss(new BigDecimal("90"))
                .takeProfit(new BigDecimal("120")).account(ownerAccount).build());
    }

    @Test
    void secondUserCannotReadListOrMutatePersistedTrade() throws Exception {
        UserDto principal = UserDto.builder().userId(otherUser.getUserId())
                .username(otherUser.getUsername()).email(otherUser.getEmail())
                .role(Role.ROLE_USER).build();
        var auth = authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        assertThatThrownBy(() -> mockMvc.perform(
                get("/api/v1/trades/{tradeId}", ownerTrade.getTradeId()).with(auth)))
                .hasRootCauseInstanceOf(com.hope.trading.trading_core.exception.EntityNotFoundException.class);
        assertThatThrownBy(() -> mockMvc.perform(
                get("/api/v1/trades").param("accountId", ownerAccount.getAccountId().toString()).with(auth)))
                .hasRootCauseInstanceOf(com.hope.trading.trading_core.exception.EntityNotFoundException.class);
        assertThatThrownBy(() -> mockMvc.perform(
                patch("/api/v1/trades/{tradeId}/stop-loss", ownerTrade.getTradeId())
                        .param("stopLoss", "80").with(auth)))
                .hasRootCauseInstanceOf(com.hope.trading.trading_core.exception.EntityNotFoundException.class);
        assertThatThrownBy(() -> mockMvc.perform(
                patch("/api/v1/trades/{tradeId}/take-profit", ownerTrade.getTradeId())
                        .param("takeProfit", "130").with(auth)))
                .hasRootCauseInstanceOf(com.hope.trading.trading_core.exception.EntityNotFoundException.class);

        Trade persisted = trades.findById(ownerTrade.getTradeId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(persisted.getStopLoss()).isEqualByComparingTo("90");
        org.assertj.core.api.Assertions.assertThat(persisted.getTakeProfit()).isEqualByComparingTo("120");
    }
}
