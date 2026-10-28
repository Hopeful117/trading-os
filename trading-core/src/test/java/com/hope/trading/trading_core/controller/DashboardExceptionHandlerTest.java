package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.dashboard.service.DashboardSnapshotDeferredException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.dashboard.service.DashboardQueryService;
import com.hope.trading.trading_core.dto.UserDto;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardExceptionHandlerTest {
    @Test
    void exposesSnapshotDeferredContractToDashboardClients() {
        var response = new DashboardExceptionHandler()
                .snapshotDeferred(new DashboardSnapshotDeferredException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "BROKER_SNAPSHOT_DEFERRED");
    }

    @Test
    void preservesTheSameMachineReadableCodeAfterJsonSerialization() throws Exception {
        var response = new DashboardExceptionHandler()
                .snapshotDeferred(new DashboardSnapshotDeferredException());
        var body = new ObjectMapper().writeValueAsString(response.getBody());

        assertThat(new ObjectMapper().readTree(body).path("code").asText())
                .isEqualTo("BROKER_SNAPSHOT_DEFERRED");
    }

    @Test
    void exposesTheDeferredContractThroughTheDashboardHttpBoundary() throws Exception {
        var dashboardQueryService = mock(DashboardQueryService.class);
        var accountId = java.util.UUID.randomUUID();
        when(dashboardQueryService.findDashboard(accountId, "alice"))
                .thenThrow(new DashboardSnapshotDeferredException());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboardQueryService))
                .setControllerAdvice(new DashboardExceptionHandler())
                .build();

        var authentication = new UsernamePasswordAuthenticationToken(
                UserDto.builder().username("alice").build(), null);
        mvc.perform(get("/api/v1/accounts/{accountId}/dashboard", accountId)
                        .with(request -> {
                            request.setUserPrincipal(authentication);
                            return request;
                        }))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BROKER_SNAPSHOT_DEFERRED"));
    }
}
