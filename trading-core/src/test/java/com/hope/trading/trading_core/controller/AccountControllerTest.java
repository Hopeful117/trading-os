package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.broker.service.BrokerSynchronizationService;
import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.service.AccountService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountControllerTest {

    private final AccountService accountService = mock(AccountService.class);
    private final BrokerSynchronizationService brokerSynchronizationService =
            mock(BrokerSynchronizationService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new AccountController(accountService, brokerSynchronizationService)).build();

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
}
