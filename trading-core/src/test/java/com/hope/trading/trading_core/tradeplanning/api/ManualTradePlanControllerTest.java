package com.hope.trading.trading_core.tradeplanning.api;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.tradeplanning.application.ManualTradePlanException;
import com.hope.trading.trading_core.tradeplanning.application.ManualTradePlanOrchestrationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualTradePlanControllerTest {
    @Test
    void createsPlanUsingAuthenticatedUser() {
        ManualTradePlanOrchestrationService service = mock(ManualTradePlanOrchestrationService.class);
        ManualTradePlanController controller = new ManualTradePlanController(service);
        UUID actorId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        ManualTradePlanRequest request = request();
        when(service.create(eq(actorId), any(), eq("key-1")))
                .thenReturn(new ManualTradePlanOrchestrationService.Response(planId, 1));

        ResponseEntity<OpportunityTradePlanController.Response> response = controller.create(
                "key-1", request, new UsernamePasswordAuthenticationToken(
                        UserDto.builder().userId(actorId).build(), null));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().tradePlanId()).isEqualTo(planId);
        verify(service).create(actorId, request, "key-1");
    }

    @Test
    void rejectsMissingAuthentication() {
        ManualTradePlanController controller = new ManualTradePlanController(
                mock(ManualTradePlanOrchestrationService.class));

        assertThatThrownBy(() -> controller.create("key-1", request(), null))
                .isInstanceOf(ManualTradePlanException.class)
                .extracting("code")
                .isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void mapsCommandFailureToResponse() {
        ManualTradePlanExceptionHandler handler = new ManualTradePlanExceptionHandler();
        ResponseEntity<java.util.Map<String, String>> response = handler.command(
                new ManualTradePlanException("MARKET_NOT_FOUND", "Market does not exist", 404));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).containsEntry("code", "MARKET_NOT_FOUND");
    }

    private ManualTradePlanRequest request() {
        return new ManualTradePlanRequest(UUID.randomUUID(), UUID.randomUUID(), "LONG", "MARKET", null,
                BigDecimal.valueOf(100), BigDecimal.valueOf(90), "Invalidation below support",
                List.of(new ManualTradePlanRequest.TakeProfit(BigDecimal.valueOf(120), BigDecimal.valueOf(100))),
                BigDecimal.ONE, BigDecimal.ONE, "Manual setup", Set.of("Price confirms"),
                Set.of("Support breaks"), Set.of());
    }
}
