package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountSnapshotIncompleteException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
