package com.hope.trading.broker_service.ctrader.authentication;

import com.hope.trading.broker_service.security.BrokerPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CTraderAuthenticationControllerTest {
    private final CTraderAuthenticationService service = mock(CTraderAuthenticationService.class);
    private final CTraderAuthenticationController controller = new CTraderAuthenticationController(service);
    private final UUID ownerId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();
    private final Authentication authentication = authentication();

    @Test
    void delegatesInitiationAndBindingToTheAuthenticatedOwner() {
        var response = response(attemptId, CTraderAuthorizationAttemptStatus.INITIATED);
        when(service.initiate(ownerId, accountId, "Bearer token")).thenReturn(response);
        when(service.bind(ownerId, accountId, attemptId, "9001", "Bearer token")).thenReturn(response);

        assertThat(controller.initiate(accountId, "Bearer token", authentication).getBody()).isSameAs(response);
        assertThat(controller.bind(accountId, attemptId, new CTraderBindAccountRequest("9001"),
                "Bearer token", authentication).getBody()).isSameAs(response);
        verify(service).initiate(ownerId, accountId, "Bearer token");
        verify(service).bind(ownerId, accountId, attemptId, "9001", "Bearer token");
    }

    @Test
    void redirectsCallbackToCompletedOrFailedAccountPage() {
        var completed = controller.callback("state", "code", null);
        assertThat(completed.getStatusCode().value()).isEqualTo(303);
        assertThat(completed.getHeaders().getLocation()).hasToString("/accounts?ctrader=completed");

        doThrow(new CTraderAuthenticationException("invalid callback"))
                .when(service).callback("bad-state", "code", null);
        var failed = controller.callback("bad-state", "code", null);
        assertThat(failed.getStatusCode().value()).isEqualTo(303);
        assertThat(failed.getHeaders().getLocation()).hasToString("/accounts?ctrader=failed");

        doThrow(new IllegalStateException("unexpected"))
                .when(service).callback("runtime", "code", null);
        assertThat(controller.callback("runtime", "code", null).getHeaders().getLocation())
                .hasToString("/accounts?ctrader=failed");
    }

    @Test
    void returnsOnlyResponsesBelongingToTheRequestedAttempt() {
        var matching = response(attemptId, CTraderAuthorizationAttemptStatus.BOUND);
        when(service.get(ownerId, accountId, attemptId)).thenReturn(matching);
        assertThat(controller.get(accountId, attemptId, authentication).getStatusCode().value()).isEqualTo(200);

        var otherAttempt = response(UUID.randomUUID(), CTraderAuthorizationAttemptStatus.BOUND);
        when(service.get(ownerId, accountId, attemptId)).thenReturn(otherAttempt);
        assertThat(controller.get(accountId, attemptId, authentication).getStatusCode().value()).isEqualTo(404);
    }

    private Authentication authentication() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new BrokerPrincipal(ownerId, "user", "USER"));
        return authentication;
    }

    private CTraderAuthorizationResponse response(UUID id, CTraderAuthorizationAttemptStatus status) {
        return new CTraderAuthorizationResponse(id, "https://auth", status, List.of(), null,
                Instant.parse("2026-11-14T00:00:00Z"), null);
    }
}
