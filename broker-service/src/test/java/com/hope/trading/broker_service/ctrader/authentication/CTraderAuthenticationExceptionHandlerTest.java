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
}
