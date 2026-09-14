package com.hope.trading.trading_core.execution.infrastructure.mapper;

import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for ExecutionRequestMapper.
 * Protects the mapping contract between Trading Core domain and broker API.
 */
class ExecutionRequestMapperRegressionTest {

    private final ExecutionRequestMapper mapper = new ExecutionRequestMapper();

    @Test
    void mapExecutionRequestTransfersAllFields() {
        var intentId = ExecutionIntentId.newId();
        var attemptId = ExecutionAttemptId.newId();
        var idempotencyKey = new IdempotencyKey("test-key");
        var brokerAccountId = UUID.randomUUID();
        var params = new ExecutionParameters(
                "BTC/USD",
                ExecutionParameters.Side.BUY,
                ExecutionParameters.OrderType.LIMIT,
                new BigDecimal("0.5"),
                new BigDecimal("50000"));

        var domainRequest = new BrokerExecutionPort.ExecutionRequest(
                intentId, attemptId, idempotencyKey, brokerAccountId, params);

        var brokerRequest = mapper.map(domainRequest);

        assertThat(brokerRequest.executionIntentId()).isEqualTo(intentId.value());
        assertThat(brokerRequest.executionAttemptId()).isEqualTo(attemptId.value());
        assertThat(brokerRequest.idempotencyKey()).isEqualTo("test-key");
        assertThat(brokerRequest.brokerAccountId()).isEqualTo(brokerAccountId);
        assertThat(brokerRequest.instrument()).isEqualTo("BTC/USD");
        assertThat(brokerRequest.side()).isEqualTo("BUY");
        assertThat(brokerRequest.orderType()).isEqualTo("LIMIT");
        assertThat(brokerRequest.quantity()).isEqualByComparingTo("0.5");
        assertThat(brokerRequest.limitPrice()).isEqualByComparingTo("50000");
    }

    @Test
    void mapExecutionRequestWithMarketOrderHasNullLimitPrice() {
        var intentId = ExecutionIntentId.newId();
        var attemptId = ExecutionAttemptId.newId();
        var idempotencyKey = new IdempotencyKey("market-key");
        var brokerAccountId = UUID.randomUUID();
        var params = new ExecutionParameters(
                "ETH/USD",
                ExecutionParameters.Side.SELL,
                ExecutionParameters.OrderType.MARKET,
                new BigDecimal("1.0"),
                null);

        var domainRequest = new BrokerExecutionPort.ExecutionRequest(
                intentId, attemptId, idempotencyKey, brokerAccountId, params);

        var brokerRequest = mapper.map(domainRequest);

        assertThat(brokerRequest.orderType()).isEqualTo("MARKET");
        assertThat(brokerRequest.limitPrice()).isNull();
    }

    @Test
    void mapReconciliationRequestTransfersAllFields() {
        var intentId = ExecutionIntentId.newId();
        var attemptId = ExecutionAttemptId.newId();
        var idempotencyKey = new IdempotencyKey("recon-key");
        var brokerAccountId = UUID.randomUUID();

        var domainRequest = new BrokerExecutionPort.ReconciliationRequest(
                intentId, attemptId, idempotencyKey, brokerAccountId);

        var brokerRequest = mapper.map(domainRequest);

        assertThat(brokerRequest.executionIntentId()).isEqualTo(intentId.value());
        assertThat(brokerRequest.executionAttemptId()).isEqualTo(attemptId.value());
        assertThat(brokerRequest.idempotencyKey()).isEqualTo("recon-key");
        assertThat(brokerRequest.brokerAccountId()).isEqualTo(brokerAccountId);
    }

    @Test
    void mapRequiresNonNullExecutionRequest() {
        assertThatThrownBy(() -> mapper.map((BrokerExecutionPort.ExecutionRequest) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void mapRequiresNonNullReconciliationRequest() {
        assertThatThrownBy(() -> mapper.map((BrokerExecutionPort.ReconciliationRequest) null))
                .isInstanceOf(NullPointerException.class);
    }
}