package com.hope.trading.trading_core.positionclose.api;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.positionclose.api.dto.PositionCloseRequest;
import com.hope.trading.trading_core.positionclose.api.dto.PositionCloseResponse;
import com.hope.trading.trading_core.positionclose.application.service.PositionCloseService;
import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseCommand;
import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseStatus;
import com.hope.trading.trading_core.positionclose.domain.model.ReconciliationCloseResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PositionCloseControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID COMMAND_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");

    private final PositionCloseService service = mock(PositionCloseService.class);
    private final PositionCloseController controller = new PositionCloseController(service);

    @Test
    void close_returns202WithCorrectBody() {
        PositionCloseCommand command = new PositionCloseCommand(
                COMMAND_ID, ACCOUNT_ID, UUID.randomUUID(),
                "BTC-POS-1", "scope-1", "idemp-1", PositionCloseStatus.SUBMITTED,
                null, null, null, NOW, NOW, 0);

        when(service.close(USER_ID, ACCOUNT_ID, "BTC-POS-1", "idemp-1")).thenReturn(command);

        ResponseEntity<PositionCloseResponse> response = controller.close(
                ACCOUNT_ID, "idemp-1", new PositionCloseRequest("BTC-POS-1"), authentication());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().commandId()).isEqualTo(COMMAND_ID.toString());
        assertThat(response.getBody().status()).isEqualTo("SUBMITTED");
        assertThat(response.getBody().externalOrderId()).isNull();
        assertThat(response.getBody().failureReason()).isNull();
        assertThat(response.getBody().resolvedMutationScope()).isEqualTo("scope-1");
        assertThat(response.getBody().reconciliationResult()).isNull();

        verify(service).close(USER_ID, ACCOUNT_ID, "BTC-POS-1", "idemp-1");
    }

    @Test
    void reconcile_returns200WithCorrectBody() {
        PositionCloseCommand command = new PositionCloseCommand(
                COMMAND_ID, ACCOUNT_ID, UUID.randomUUID(),
                "ETH-POS-2", "scope-2", "idemp-2", PositionCloseStatus.CLOSED,
                ReconciliationCloseResult.EXPOSURE_CONFIRMED_ABSENT, "ext-order-99",
                null, NOW, NOW, 5);

        when(service.reconcile(USER_ID, COMMAND_ID)).thenReturn(command);

        ResponseEntity<PositionCloseResponse> response =
                controller.reconcile(ACCOUNT_ID, COMMAND_ID, authentication());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().commandId()).isEqualTo(COMMAND_ID.toString());
        assertThat(response.getBody().status()).isEqualTo("CLOSED");
        assertThat(response.getBody().externalOrderId()).isEqualTo("ext-order-99");
        assertThat(response.getBody().failureReason()).isNull();
        assertThat(response.getBody().resolvedMutationScope()).isEqualTo("scope-2");
        assertThat(response.getBody().reconciliationResult()).isEqualTo("EXPOSURE_CONFIRMED_ABSENT");

        verify(service).reconcile(USER_ID, COMMAND_ID);
    }

    @Test
    void toResponse_throwsIllegalStateExceptionForMalformedObject() {
        Object malformedObject = new Object();

        assertThatThrownBy(() -> {
            java.lang.reflect.Method toResponseMethod =
                    PositionCloseController.class.getDeclaredMethod("toResponse", Object.class);
            toResponseMethod.setAccessible(true);
            toResponseMethod.invoke(controller, malformedObject);
        }).isInstanceOf(java.lang.reflect.InvocationTargetException.class)
                .satisfies(throwable -> {
                    assertThat(throwable.getCause())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("Failed to extract command response");
                });
    }

    private Authentication authentication() {
        Authentication auth = mock(Authentication.class);
        UserDto userDto = new UserDto();
        userDto.setUserId(USER_ID);
        when(auth.getPrincipal()).thenReturn(userDto);
        return auth;
    }
}
