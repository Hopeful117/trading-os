package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountService;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.dto.UserDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalBrokerAccountControllerTest {

    private final BrokerAccountService service = mock(BrokerAccountService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new InternalBrokerAccountController(service)).build();

    private UsernamePasswordAuthenticationToken auth(UUID userId) {
        UserDto principal = UserDto.builder().userId(userId).build();
        return new UsernamePasswordAuthenticationToken(principal, null);
    }

    private BrokerAccountResponse response(UUID id) {
        return new BrokerAccountResponse(id, BrokerProvider.KRAKEN, "Test",
                "ext-001", BrokerConnectionStatus.CONNECTED,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void ownedReturnsAccountForPrincipal() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(service.get(userId, accountId)).thenReturn(response(accountId));

        mvc.perform(get("/internal/v1/broker-accounts/{accountId}", accountId)
                        .principal(auth(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()));

        verify(service).get(userId, accountId);
    }

    @Test
    void updateReturnsUpdatedAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID credentialRef = UUID.randomUUID();
        when(service.updateStatus(eq(userId), eq(accountId), any(), any(), any(), any()))
                .thenReturn(response(accountId));

        mvc.perform(post("/internal/v1/broker-accounts/{accountId}/connection-status", accountId)
                        .principal(auth(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONNECTED\","
                                + "\"credentialReference\":\"" + credentialRef + "\","
                                + "\"externalAccountId\":\"ext-001\","
                                + "\"validatedAt\":\"2026-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("KRAKEN"));

        verify(service).updateStatus(eq(userId), eq(accountId), any(), any(), any(), any());
    }
}
