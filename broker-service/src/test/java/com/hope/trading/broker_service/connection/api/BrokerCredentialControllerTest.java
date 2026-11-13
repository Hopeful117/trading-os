package com.hope.trading.broker_service.connection.api;

import com.hope.trading.broker_service.connection.application.BrokerCredentialConnectionService;
import com.hope.trading.broker_service.connection.application.CredentialCommandRateLimiter;
import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import com.hope.trading.broker_service.credential.domain.CredentialValidationOutcome;
import com.hope.trading.broker_service.security.BrokerPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.UUID;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BrokerCredentialControllerTest {
    private final BrokerCredentialConnectionService service = mock(BrokerCredentialConnectionService.class);
    private final CredentialCommandRateLimiter rateLimiter = mock(CredentialCommandRateLimiter.class);
    private final BrokerCredentialController controller = new BrokerCredentialController(service, rateLimiter);
    private final UUID ownerId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final Authentication authentication = mock(Authentication.class);

    @Test
    void delegatesCredentialLifecycleAndPreservesResponseStatus() {
        BrokerPrincipal principal = new BrokerPrincipal(ownerId, "user", "ROLE_USER");
        SubmitBrokerCredentialsRequest request = request("api-key-123", "api-secret-123456", null);
        CredentialValidationResponse response = new CredentialValidationResponse(
                CredentialValidationOutcome.VALID, BrokerConnectionStatus.CONNECTED, java.util.Set.of(),
                java.time.Instant.EPOCH, "ok");
        BrokerConnectionResponse connection = new BrokerConnectionResponse(
                accountId, null, BrokerConnectionStatus.CONNECTED, "external", "hint", java.util.Set.of(),
                java.time.Instant.EPOCH);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(service.connect(eq(ownerId), eq(accountId), any(), eq("Bearer token"))).thenReturn(response);
        when(service.rotate(eq(ownerId), eq(accountId), any(), eq("Bearer token"))).thenReturn(response);
        when(service.validate(ownerId, accountId, "Bearer token")).thenReturn(response);
        when(service.get(ownerId, accountId, "Bearer token")).thenReturn(connection);

        assertThat(controller.connect(accountId, request, "Bearer token", authentication).getBody()).isSameAs(response);
        assertThat(controller.rotate(accountId, request, "Bearer token", authentication).getBody()).isSameAs(response);
        assertThat(controller.validate(accountId, "Bearer token", authentication).getBody()).isSameAs(response);
        assertThat(controller.status(accountId, "Bearer token", authentication).getBody()).isSameAs(connection);
        assertThat(controller.revoke(accountId, "Bearer token", authentication).getStatusCode().value()).isEqualTo(204);
        assertThat(controller.disconnect(accountId, "Bearer token", authentication).getStatusCode().value()).isEqualTo(204);

        verify(rateLimiter, times(3)).check(ownerId, accountId);
        verify(service).revoke(ownerId, accountId, "Bearer token");
        verify(service).disconnect(ownerId, accountId, "Bearer token");
    }

    private SubmitBrokerCredentialsRequest request(String apiKey, String apiSecret, String passphrase) {
        try {
            SubmitBrokerCredentialsRequest request = new SubmitBrokerCredentialsRequest();
            set(request, "apiKey", apiKey);
            set(request, "apiSecret", apiSecret);
            set(request, "passphrase", passphrase);
            return request;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private void set(SubmitBrokerCredentialsRequest request, String fieldName, String value)
            throws ReflectiveOperationException {
        Field field = SubmitBrokerCredentialsRequest.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(request, value);
    }
}
