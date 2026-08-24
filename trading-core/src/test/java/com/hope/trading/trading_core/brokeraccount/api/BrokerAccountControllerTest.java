package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountService;
import com.hope.trading.trading_core.brokeraccount.application.BrokerConnectionCommandClient;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.dto.UserDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BrokerAccountControllerTest {

    private final BrokerAccountService service = mock(BrokerAccountService.class);
    private final BrokerConnectionCommandClient brokerConnection = mock(BrokerConnectionCommandClient.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new BrokerAccountController(service, brokerConnection)).build();

    private UsernamePasswordAuthenticationToken auth(UUID userId) {
        UserDto principal = UserDto.builder().userId(userId).build();
        return new UsernamePasswordAuthenticationToken(principal, null);
    }

    private BrokerAccountResponse response(UUID id) {
        return new BrokerAccountResponse(id, BrokerProvider.KRAKEN, "Test Account",
                "ext-123", BrokerConnectionStatus.CONNECTED,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(service.create(eq(userId), any())).thenReturn(response(accountId));

        mvc.perform(post("/api/v1/broker-accounts")
                        .principal(auth(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"KRAKEN\",\"displayName\":\"Test Account\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/broker-accounts/" + accountId))
                .andExpect(jsonPath("$.provider").value("KRAKEN"));

        verify(service).create(eq(userId), any());
    }

    @Test
    void listReturnsAccounts() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(service.list(userId)).thenReturn(List.of(response(accountId)));

        mvc.perform(get("/api/v1/broker-accounts").principal(auth(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("KRAKEN"));

        verify(service).list(userId);
    }

    @Test
    void getReturnsAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(service.get(userId, accountId)).thenReturn(response(accountId));

        mvc.perform(get("/api/v1/broker-accounts/{accountId}", accountId).principal(auth(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()));

        verify(service).get(userId, accountId);
    }

    @Test
    void statusDelegatesToGet() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(service.get(userId, accountId)).thenReturn(response(accountId));

        mvc.perform(get("/api/v1/broker-accounts/{accountId}/status", accountId).principal(auth(userId)))
                .andExpect(status().isOk());

        verify(service).get(userId, accountId);
    }

    @Test
    void disconnectCallsBrokerAndReturns() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(service.get(userId, accountId)).thenReturn(response(accountId));

        mvc.perform(post("/api/v1/broker-accounts/{accountId}/disconnect", accountId).principal(auth(userId)))
                .andExpect(status().isOk());

        verify(brokerConnection).disconnect(accountId);
        verify(service).get(userId, accountId);
    }

    @Test
    void revokeReturns204() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        mvc.perform(delete("/api/v1/broker-accounts/{accountId}", accountId).principal(auth(userId)))
                .andExpect(status().isNoContent());

        verify(brokerConnection).revoke(accountId);
    }
}
