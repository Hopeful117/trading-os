package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.broker.service.BrokerSynchronizationService;
import com.hope.trading.trading_core.brokeraccount.api.BrokerAccountExceptionHandler;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountSnapshotIncompleteException;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSynchronizationResult;
import com.hope.trading.trading_core.broker.dto.SnapshotCollectionAvailability;
import com.hope.trading.trading_core.dto.UserDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountSynchronizationV2ControllerTest {
    private final BrokerSynchronizationService synchronization = mock(BrokerSynchronizationService.class);
    private final AccountSynchronizationV2Controller controller =
            new AccountSynchronizationV2Controller(synchronization);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new BrokerAccountExceptionHandler())
            .build();

    @Test
    void incompleteSnapshotIsReturnedAsExplicitConflict() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID brokerAccountId = UUID.randomUUID();
        doThrow(new BrokerAccountSnapshotIncompleteException(
                "INCOMPLETE", "FRESH", List.of("EQUITY_UNAVAILABLE")))
                .when(synchronization).synchronize(ownerId, brokerAccountId);

        UserDto principal = UserDto.builder().userId(ownerId).build();
        mvc.perform(post("/api/v2/accounts/{brokerAccountId}/synchronize", brokerAccountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .principal(new UsernamePasswordAuthenticationToken(principal, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BROKER_ACCOUNT_SNAPSHOT_INCOMPLETE"))
                .andExpect(jsonPath("$.completeness").value("INCOMPLETE"))
                .andExpect(jsonPath("$.freshness").value("FRESH"));
    }

    @Test
    void partialSnapshotReturnsStructuredSuccessfulResult() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID brokerAccountId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-04T12:00:00Z");
        when(synchronization.synchronize(ownerId, brokerAccountId)).thenReturn(
                new BrokerAccountSynchronizationResult(brokerAccountId, "SYNCED_PARTIAL", now, now,
                        "INCOMPLETE", "FRESH", SnapshotCollectionAvailability.AVAILABLE_EMPTY,
                        SnapshotCollectionAvailability.AVAILABLE_EMPTY,
                        SnapshotCollectionAvailability.UNAVAILABLE_OR_ABSENT,
                        SnapshotCollectionAvailability.UNAVAILABLE_OR_ABSENT,
                        List.of("ACCOUNT_FACTS", "BALANCES", "POSITIONS"),
                        List.of("EQUITY_UNAVAILABLE", "DEAL_HISTORY_UNAVAILABLE"), true));

        UserDto principal = UserDto.builder().userId(ownerId).build();
        mvc.perform(post("/api/v2/accounts/{brokerAccountId}/synchronize", brokerAccountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(new UsernamePasswordAuthenticationToken(principal, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SYNCED_PARTIAL"))
                .andExpect(jsonPath("$.completeness").value("INCOMPLETE"))
                .andExpect(jsonPath("$.diagnosticOnly").value(true))
                .andExpect(jsonPath("$.unavailabilityReasons[0]").value("EQUITY_UNAVAILABLE"));
    }
}
