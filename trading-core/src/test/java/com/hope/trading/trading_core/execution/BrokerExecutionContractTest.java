package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.execution.infrastructure.adapter.BrokerExecutionClient;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import static org.assertj.core.api.Assertions.*;

class BrokerExecutionContractTest {
    @Test void consumerUsesTheBrokerServiceV1Contract(){assertThat(Arrays.stream(BrokerExecutionClient.BrokerRequest.class.getRecordComponents()).map(RecordComponent::getName)).containsExactly("executionIntentId","executionAttemptId","idempotencyKey","brokerAccountId","instrument","side","orderType","quantity","limitPrice");assertThat(Arrays.stream(BrokerExecutionClient.BrokerResponse.class.getRecordComponents()).map(RecordComponent::getName)).containsExactly("outcome","externalOrderId","correlationId","status","reasonCode");}
    @Test void executionEndpointsRemainStable() throws Exception {assertThat(BrokerExecutionClient.class.getMethod("submit",BrokerExecutionClient.BrokerRequest.class).getAnnotation(PostMapping.class).value()).containsExactly("/internal/v1/executions");assertThat(BrokerExecutionClient.class.getMethod("reconcile",BrokerExecutionClient.ReconcileRequest.class).getAnnotation(PostMapping.class).value()).containsExactly("/internal/v1/executions/reconcile");}
}
