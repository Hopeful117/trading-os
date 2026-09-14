package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests ensuring BrokerExecutionPort contract remains unchanged.
 * These tests protect the port interface from accidental modification that
 * would break the execution adapter pattern.
 */
class BrokerExecutionPortContractRegressionTest {

    @Test
    void acknowledgedRecordRequiresNonNullExternalOrderId() {
        assertThatThrownBy(() -> new BrokerExecutionPort.Acknowledged(null, "corr-1"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void acknowledgedRecordRequiresNonNullCorrelationId() {
        assertThatThrownBy(() -> new BrokerExecutionPort.Acknowledged("ext-1", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void acknowledgedRecordAcceptsValidValues() {
        var acknowledged = new BrokerExecutionPort.Acknowledged("ext-1", "corr-1");

        assertThat(acknowledged.externalOrderId()).isEqualTo("ext-1");
        assertThat(acknowledged.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void rejectedRecordAcceptsNullValues() {
        var rejected = new BrokerExecutionPort.Rejected(null, null);

        assertThat(rejected.externalOrderId()).isNull();
        assertThat(rejected.reasonCode()).isNull();
    }

    @Test
    void rejectedRecordAcceptsValidValues() {
        var rejected = new BrokerExecutionPort.Rejected("ext-1", "INSUFFICIENT_FUNDS");

        assertThat(rejected.externalOrderId()).isEqualTo("ext-1");
        assertThat(rejected.reasonCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void unknownRecordAcceptsNullReasonCode() {
        var unknown = new BrokerExecutionPort.Unknown(null);

        assertThat(unknown.reasonCode()).isNull();
    }

    @Test
    void unknownRecordAcceptsValidReasonCode() {
        var unknown = new BrokerExecutionPort.Unknown("BROKER_OUTCOME_UNKNOWN");

        assertThat(unknown.reasonCode()).isEqualTo("BROKER_OUTCOME_UNKNOWN");
    }

    @Test
    void executionRequestAcceptsNullFields() {
        var request = new BrokerExecutionPort.ExecutionRequest(
                null, null, null, null, null);

        assertThat(request.intentId()).isNull();
        assertThat(request.attemptId()).isNull();
        assertThat(request.idempotencyKey()).isNull();
        assertThat(request.brokerAccountId()).isNull();
        assertThat(request.parameters()).isNull();
    }

    @Test
    void executionRequestAcceptsValidValues() {
        var request = new BrokerExecutionPort.ExecutionRequest(
                com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId.newId(),
                com.hope.trading.trading_core.execution.domain.valueobject.ExecutionAttemptId.newId(),
                new com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey("key"),
                UUID.randomUUID(),
                new com.hope.trading.trading_core.execution.domain.model.ExecutionParameters(
                        "BTC/USD",
                        com.hope.trading.trading_core.execution.domain.model.ExecutionParameters.Side.BUY,
                        com.hope.trading.trading_core.execution.domain.model.ExecutionParameters.OrderType.MARKET,
                        new java.math.BigDecimal("0.1"),
                        null));

        assertThat(request.brokerAccountId()).isNotNull();
        assertThat(request.parameters()).isNotNull();
    }

    @Test
    void reconciliationRequestAcceptsNullFields() {
        var request = new BrokerExecutionPort.ReconciliationRequest(
                null, null, null, null);

        assertThat(request.intentId()).isNull();
        assertThat(request.attemptId()).isNull();
        assertThat(request.idempotencyKey()).isNull();
        assertThat(request.brokerAccountId()).isNull();
    }

    @Test
    void reconciliationRequestAcceptsValidValues() {
        var request = new BrokerExecutionPort.ReconciliationRequest(
                com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId.newId(),
                com.hope.trading.trading_core.execution.domain.valueobject.ExecutionAttemptId.newId(),
                new com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey("key"),
                UUID.randomUUID());

        assertThat(request.brokerAccountId()).isNotNull();
    }

    @Test
    void reconciledOrderRecordAcceptsValidValues() {
        var reconciled = new BrokerExecutionPort.ReconciledOrder(
                "ext-1", "corr-1", com.hope.trading.trading_core.execution.domain.valueobject.BrokerOrderStatus.FILLED);

        assertThat(reconciled.externalOrderId()).isEqualTo("ext-1");
        assertThat(reconciled.correlationId()).isEqualTo("corr-1");
        assertThat(reconciled.status()).isEqualTo(com.hope.trading.trading_core.execution.domain.valueobject.BrokerOrderStatus.FILLED);
    }

    @Test
    void inconsistentRecordAcceptsValidReasonCode() {
        var inconsistent = new BrokerExecutionPort.Inconsistent("MULTIPLE_MATCHING_ORDERS");

        assertThat(inconsistent.reasonCode()).isEqualTo("MULTIPLE_MATCHING_ORDERS");
    }
}
