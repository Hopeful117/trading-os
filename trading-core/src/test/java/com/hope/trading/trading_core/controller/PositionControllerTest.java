package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.broker.apiClient.BrokerApiClient;
import com.hope.trading.trading_core.broker.dto.AccountBalanceDto;
import com.hope.trading.trading_core.broker.dto.BrokerAccountDto;
import com.hope.trading.trading_core.dashboard.integration.BrokerDashboardMapper;
import com.hope.trading.trading_core.dashboard.model.OpenPositionDashboardView;
import com.hope.trading.trading_core.dashboard.service.PositionQueryService;
import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.service.AccountService;
import com.hope.trading.trading_core.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PositionControllerTest {

    private final AccountService accountService = mock(AccountService.class);
    private final BrokerApiClient brokerApiClient = mock(BrokerApiClient.class);
    private final BrokerDashboardMapper brokerMapper = new BrokerDashboardMapper();
    private final PositionQueryService positionQueryService = mock(PositionQueryService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new PositionController(accountService, brokerApiClient, brokerMapper, positionQueryService)
    ).build();

    private UUID accountId;
    private String username;
    private Account account;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        username = "trader@test.com";
        account = new Account();
        account.setAccountId(accountId);
        account.setName("Test Account");
        account.setBaseCurrency("USD");
        account.setBalances(List.of());
    }

    @Test
    void findPositionsReturnsEmptyListWhenNoBrokerData() throws Exception {
        when(accountService.getAccountById(accountId, username)).thenReturn(account);
        when(brokerApiClient.getAccount()).thenThrow(new RuntimeException("Broker unavailable"));

        mvc.perform(get("/api/v1/accounts/{accountId}/positions", accountId)
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void findPositionsReturnsEmptyListWhenNoOpenPositions() throws Exception {
        when(accountService.getAccountById(accountId, username)).thenReturn(account);
        when(brokerApiClient.getAccount()).thenReturn(emptyBrokerDto());
        when(positionQueryService.findPositions(eq(accountId), any(), any(), any()))
                .thenReturn(List.of());

        mvc.perform(get("/api/v1/accounts/{accountId}/positions", accountId)
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void findPositionsReturnsPositionList() throws Exception {
        when(accountService.getAccountById(accountId, username)).thenReturn(account);
        when(brokerApiClient.getAccount()).thenReturn(emptyBrokerDto());

        OpenPositionDashboardView position = new OpenPositionDashboardView(
                "pos-1", accountId, UUID.randomUUID(), "BTC/USD",
                TradeType.BUY, new BigDecimal("0.1"), new BigDecimal("50000"),
                new BigDecimal("51000"), null, null,
                new BigDecimal("100"), new BigDecimal("2"),
                new BigDecimal("100"), new BigDecimal("0"), new BigDecimal("0"),
                new BigDecimal("5100"), com.hope.trading.trading_core.dashboard.model.PositionProtectionStatus.MISSING_STOP_LOSS,
                true, Instant.now(), Instant.now(), Instant.now()
        );
        when(positionQueryService.findPositions(eq(accountId), any(), any(), any()))
                .thenReturn(List.of(position));

        mvc.perform(get("/api/v1/accounts/{accountId}/positions", accountId)
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].positionId").value("pos-1"))
                .andExpect(jsonPath("$[0].symbol").value("BTC/USD"))
                .andExpect(jsonPath("$[0].side").value("BUY"));
    }

    @Test
    void nonOwnerCannotRetrievePositions() {
        UUID foreignAccountId = UUID.randomUUID();
        when(accountService.getAccountById(foreignAccountId, username))
                .thenThrow(new EntityNotFoundException("account not found"));

        Exception exception = assertThrows(Exception.class, () ->
                mvc.perform(get("/api/v1/accounts/{accountId}/positions", foreignAccountId)
                                .principal(authentication()))
        );

        assertThat(exception).hasCauseInstanceOf(EntityNotFoundException.class);
    }

    private BrokerAccountDto emptyBrokerDto() {
        BrokerAccountDto dto = new BrokerAccountDto();
        dto.setBrokerAccountId("broker-1");
        dto.setBroker("KRAKEN");
        dto.setBaseCurrency("USD");
        dto.setBalances(new AccountBalanceDto());
        dto.setOpenTrades(List.of());
        dto.setDataAt(Instant.now());
        return dto;
    }

    private UsernamePasswordAuthenticationToken authentication() {
        UserDto user = new UserDto();
        user.setUsername(username);
        return new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
