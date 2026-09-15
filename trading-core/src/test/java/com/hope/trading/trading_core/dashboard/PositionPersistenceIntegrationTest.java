package com.hope.trading.trading_core.dashboard;

import com.hope.trading.trading_core.broker.apiClient.BrokerApiClient;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.TradeRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PositionPersistenceIntegrationTest {
    private static final Instant OPENED_AT = Instant.parse("2026-08-15T10:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private AccountRepository accounts;
    @Autowired private TradeRepository trades;
    @Autowired private BrokerAccountRepository brokerAccounts;
    @Autowired private EntityManager entityManager;

    @MockitoBean private BrokerApiClient brokerApiClient;
    @MockitoBean private MarketDataClient marketDataClient;

    private User owner;
    private UUID accountId;
    private UUID brokerAccountId;
    private UUID tradeId;

    @BeforeEach
    void setUp() {
        owner = users.saveAndFlush(User.builder()
                .username("position-integration-" + UUID.randomUUID())
                .password("password")
                .email(UUID.randomUUID() + "@test.local")
                .role(Role.ROLE_USER)
                .build());

        BrokerAccount brokerAccount = BrokerAccount.create(
                owner.getUserId(), BrokerProvider.KRAKEN, ExecutionMode.PAPER,
                "Persisted Paper", OPENED_AT);
        brokerAccountId = brokerAccount.id();
        brokerAccounts.saveAndFlush(brokerAccount);

        Account account = Account.builder()
                .brokerAccountId(brokerAccountId)
                .broker(BrokerProvider.KRAKEN.name())
                .name("Persisted Paper")
                .baseCurrency("USD")
                .equity(new BigDecimal("10000"))
                .peakEquity(new BigDecimal("10000"))
                .user(owner)
                .build();
        accountId = accounts.saveAndFlush(account).getAccountId();

        Trade trade = Trade.builder()
                .symbol("BTC/USD")
                .type(TradeType.BUY)
                .quantity(new BigDecimal("0.1"))
                .entryPrice(new BigDecimal("50000"))
                .openedAt(OPENED_AT)
                .tradeStatus(TradeStatus.OPEN)
                .account(account)
                .build();
        tradeId = trades.saveAndFlush(trade).getTradeId();

        when(marketDataClient.findAll()).thenReturn(List.of());
    }

    @Test
    void returnsPersistedPaperOpenTradeAfterPersistenceContextReloadThroughHttp() throws Exception {
        entityManager.flush();
        entityManager.clear();

        org.assertj.core.api.Assertions.assertThat(accountId).isNotEqualTo(brokerAccountId);

        UserDto principal = UserDto.builder()
                .userId(owner.getUserId())
                .username(owner.getUsername())
                .email(owner.getEmail())
                .role(owner.getRole())
                .build();

        mvc.perform(get("/api/v1/accounts/{accountId}/positions", accountId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].positionId").value(tradeId.toString()))
                .andExpect(jsonPath("$[0].accountId").value(accountId.toString()))
                .andExpect(jsonPath("$[0].source").value("TRADING_CORE"))
                .andExpect(jsonPath("$[0].valuationStatus").value("UNKNOWN_MARKET"))
                .andExpect(jsonPath("$[0].symbol").value("BTC/USD"))
                .andExpect(jsonPath("$[0].side").value("BUY"))
                .andExpect(jsonPath("$[0].quantity").value(0.1))
                .andExpect(jsonPath("$[0].entryPrice").value(50000));

        verifyNoInteractions(brokerApiClient);

        entityManager.clear();
        Trade persistedTrade = trades.findById(tradeId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(persistedTrade.getCurrentPrice()).isNull();
        org.assertj.core.api.Assertions.assertThat(persistedTrade.getPnl()).isNull();
    }
}
