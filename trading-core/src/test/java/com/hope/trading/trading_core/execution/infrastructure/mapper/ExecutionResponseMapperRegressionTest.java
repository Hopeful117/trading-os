package com.hope.trading.trading_core.execution.infrastructure.mapper;

import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.domain.valueobject.BrokerOrderStatus;
import com.hope.trading.trading_core.execution.infrastructure.adapter.BrokerExecutionClient.BrokerResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for ExecutionResponseMapper.
 * Protects the mapping contract between broker API response and Trading Core domain.
 */
class ExecutionResponseMapperRegressionTest {

    private final ExecutionResponseMapper mapper = new ExecutionResponseMapper();

    @Test
    void submissionMapsAcknowledged() {
        var response = new BrokerResponse(
                "ACKNOWLEDGED", "ext-order-1", "corr-1", "ACKNOWLEDGED", null);

        var result = mapper.submission(response);

        assertThat(result).isInstanceOf(BrokerExecutionPort.Acknowledged.class);
        var ack = (BrokerExecutionPort.Acknowledged) result;
        assertThat(ack.externalOrderId()).isEqualTo("ext-order-1");
        assertThat(ack.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void submissionMapsRejected() {
        var response = new BrokerResponse(
                "REJECTED", "ext-order-2", null, "REJECTED", "INSUFFICIENT_FUNDS");

        var result = mapper.submission(response);

        assertThat(result).isInstanceOf(BrokerExecutionPort.Rejected.class);
        var rejected = (BrokerExecutionPort.Rejected) result;
        assertThat(rejected.externalOrderId()).isEqualTo("ext-order-2");
        assertThat(rejected.reasonCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void submissionMapsRejectedWithNullExternalOrderId() {
        var response = new BrokerResponse(
                "REJECTED", null, null, "REJECTED", "INVALID_ORDER");

        var result = mapper.submission(response);

        assertThat(result).isInstanceOf(BrokerExecutionPort.Rejected.class);
        var rejected = (BrokerExecutionPort.Rejected) result;
        assertThat(rejected.externalOrderId()).isNull();
        assertThat(rejected.reasonCode()).isEqualTo("INVALID_ORDER");
    }

    @Test
    void submissionMapsUnknown() {
        var response = new BrokerResponse(
                "UNKNOWN", null, null, "UNKNOWN", "TIMEOUT");

        var result = mapper.submission(response);

        assertThat(result).isInstanceOf(BrokerExecutionPort.Unknown.class);
        var unknown = (BrokerExecutionPort.Unknown) result;
        assertThat(unknown.reasonCode()).isEqualTo("TIMEOUT");
    }

    @Test
    void submissionMapsUnknownForUnexpectedOutcome() {
        var response = new BrokerResponse(
                "WEIRD_OUTCOME", null, null, "WEIRD", "UNEXPECTED");

        var result = mapper.submission(response);

        assertThat(result).isInstanceOf(BrokerExecutionPort.Unknown.class);
        var unknown = (BrokerExecutionPort.Unknown) result;
        assertThat(unknown.reasonCode()).isEqualTo("UNEXPECTED");
    }

    @Test
    void submissionRequiresNonNullResponse() {
        assertThatThrownBy(() -> mapper.submission(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void reconciliationMapsFound() {
        var response = new BrokerResponse(
                "FOUND", "ext-order-1", "corr-1", "FILLED", null);

        var result = mapper.reconciliation(response);

        assertThat(result).isInstanceOf(BrokerExecutionPort.ReconciledOrder.class);
        var reconciled = (BrokerExecutionPort.ReconciledOrder) result;
        assertThat(reconciled.externalOrderId()).isEqualTo("ext-order-1");
        assertThat(reconciled.correlationId()).isEqualTo("corr-1");
        assertThat(reconciled.status()).isEqualTo(BrokerOrderStatus.FILLED);
    }

    @Test
    void reconciliationMapsAbsent() {
        var response = new BrokerResponse(
                "ABSENT", null, null, null, null);

        var result = mapper.reconciliation(response);

        assertThat(result).isInstanceOf(BrokerExecutionPort.ConfirmedAbsent.class);
    }

    @Test
    void reconciliationMapsInconsistentForUnexpectedOutcome() {
        var response = new BrokerResponse(
                "WEIRD", null, null, "WEIRD", "INCONSISTENT_REASON");

        var result = mapper.reconciliation(response);

        assertThat(result).isInstanceOf(BrokerExecutionPort.Inconsistent.class);
        var inconsistent = (BrokerExecutionPort.Inconsistent) result;
        assertThat(inconsistent.reasonCode()).isEqualTo("INCONSISTENT_REASON");
    }

    @Test
    void reconciliationRequiresNonNullResponse() {
        assertThatThrownBy(() -> mapper.reconciliation(null))
                .isInstanceOf(NullPointerException.class);
    }
}