package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountSnapshotIncompleteException;
import feign.FeignException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class BrokerAccountExceptionHandlerTest {
    private final BrokerAccountExceptionHandler handler = new BrokerAccountExceptionHandler();

    @Test
    void incompleteSnapshotIsReportedAsConflictInsteadOfForbidden() {
        var response = handler.snapshotIncomplete(new BrokerAccountSnapshotIncompleteException(
                "INCOMPLETE", "FRESH", List.of("EQUITY_UNAVAILABLE")));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "BROKER_ACCOUNT_SNAPSHOT_INCOMPLETE")
                .containsEntry("completeness", "INCOMPLETE")
                .containsEntry("freshness", "FRESH")
                .containsEntry("reasons", List.of("EQUITY_UNAVAILABLE"));
    }

    @Test
    void deferredSnapshotFromBrokerIsReportedAsConflictInsteadOfForbidden() {
        var exception = org.mockito.Mockito.mock(FeignException.Conflict.class);
        when(exception.contentUTF8()).thenReturn("{\"code\":\"BROKER_SNAPSHOT_DEFERRED\"}");
        var response = handler.snapshotDeferred(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "BROKER_SNAPSHOT_DEFERRED");
    }

    @Test
    void providerConflictIsNotReportedAsSnapshotDeferred() {
        var exception = org.mockito.Mockito.mock(FeignException.Conflict.class);
        when(exception.contentUTF8()).thenReturn("{\"message\":\"authentication failed\"}");
        var response = handler.snapshotDeferred(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "BROKER_PROVIDER_CONFLICT");
    }
}
