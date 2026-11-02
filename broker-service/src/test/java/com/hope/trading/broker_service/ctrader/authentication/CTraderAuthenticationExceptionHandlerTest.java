package com.hope.trading.broker_service.ctrader.authentication;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class CTraderAuthenticationExceptionHandlerTest {
    private final CTraderAuthenticationExceptionHandler handler =
            new CTraderAuthenticationExceptionHandler();

    @Test
    void exposesMachineReadableCodeForSnapshotCooldown() {
        var response = handler.handleSnapshotDeferred(new CTraderSnapshotDeferredException(
                "cTrader snapshot blocked by cooldown or concurrent synchronization"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "BROKER_SNAPSHOT_DEFERRED");
    }

    @Test
    void doesNotClassifyOtherSafetyFailuresAsSnapshotCooldown() {
        var response = handler.handle(new CTraderSafetyException(
                "cTrader execution is disabled by safety policy"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).doesNotContainKey("code");
    }

    @Test
    void doesNotClassifyAuthenticationFailuresAsSnapshotCooldown() {
        var response = handler.handle(new CTraderAuthenticationException("authentication failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).doesNotContainKey("code");
    }

    @Test
    void exposesSafeStructuredProviderDiagnostic() {
        var response = handler.handle(new CTraderAuthenticationException("account disconnected",
                new CTraderAuthenticationException.ProviderDiagnostic(
                        "ACCOUNT_AUTH", 2102, "ProtoOAAccountAuthReq", 2142,
                        "ProtoOAAccountDisconnectEvent", null, "Access Token is expired",
                        "accessToken=secret\nAuthorization: Bearer token", "corr-1")));

        assertThat(response.getBody())
                .containsEntry("stage", "ACCOUNT_AUTH")
                .containsEntry("requestPayloadType", "2102")
                .containsEntry("responsePayloadType", "2142")
                .containsEntry("providerReason", "Access Token is expired")
                .containsEntry("providerDescription",
                        "accessToken=[REDACTED] Authorization=[REDACTED]")
                .doesNotContainKey("accessToken")
                .doesNotContainKey("refreshToken")
                .doesNotContainKey("clientSecret");
    }
}
