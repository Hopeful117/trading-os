package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.broker.service.BrokerSynchronizationService;
import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.service.AccountService;
import com.hope.trading.trading_core.risk.infrastructure.client.BrokerCapabilityQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountControllerTest {

    private final AccountService accountService = mock(AccountService.class);
    private final BrokerSynchronizationService brokerSynchronizationService =
            mock(BrokerSynchronizationService.class);
    private final BrokerCapabilityQueryService brokerCapabilityQueryService =
            mock(BrokerCapabilityQueryService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new AccountController(accountService, brokerSynchronizationService, brokerCapabilityQueryService)).build();

    @Test
    void singularAccountEndpointReturnsEnrichedProfiles() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(accountService.getAccountDtoById(eq(accountId), eq("trader")))
                .thenReturn(AccountDto.builder()
                        .accountId(accountId)
                        .riskProfileId(UUID.randomUUID())
                        .riskProfileSemanticVersion("1.0.0")
                        .tradePlanningProfileId(UUID.randomUUID())
                        .tradePlanningProfileVersion(1L)
                        .build());

        mvc.perform(get("/api/v1/accounts/{accountId}", accountId)
                        .principal(new UsernamePasswordAuthenticationToken(
                                UserDto.builder().username("trader").build(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskProfileId").isNotEmpty())
                .andExpect(jsonPath("$.riskProfileSemanticVersion").value("1.0.0"))
                .andExpect(jsonPath("$.tradePlanningProfileId").isNotEmpty())
                .andExpect(jsonPath("$.tradePlanningProfileVersion").value(1));

        verify(accountService).getAccountDtoById(accountId, "trader");
    }

    @Test
    void marketCapabilitiesUseAuthenticatedPrincipalForAccountOwnership() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(brokerCapabilityQueryService.resolve(eq(accountId), eq("trader"), eq(java.util.List.of("BTC/USD"))))
                .thenReturn(java.util.List.of(new BrokerCapabilityQueryService.MarketCapability(
                        "BTC/USD", true, java.util.List.of("MARKET"), java.util.List.of(),
                        java.util.List.of(), 1, java.time.Instant.parse("2026-10-09T00:00:00Z"),
                        "KRAKEN", java.util.List.of())));

        mvc.perform(post("/api/v1/accounts/{accountId}/market-capabilities", accountId)
                        .principal(new UsernamePasswordAuthenticationToken(
                                UserDto.builder().username("trader").build(), null))
                        .contentType("application/json")
                        .content("{\"instruments\":[\"BTC/USD\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[0].provider").value("KRAKEN"));

        verify(brokerCapabilityQueryService).resolve(accountId, "trader", java.util.List.of("BTC/USD"));
    }
}
