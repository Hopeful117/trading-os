package com.hope.trading.trading_core.execution.infrastructure.adapter;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.UUID;

@FeignClient(name="broker-service",contextId="execution-broker-client")
public interface BrokerExecutionClient {
    @PostMapping("/internal/v1/executions") BrokerResponse submit(@RequestBody BrokerRequest request);
    @PostMapping("/internal/v1/executions/reconcile") BrokerResponse reconcile(@RequestBody ReconcileRequest request);
    @PostMapping("/internal/v1/executions/{externalOrderId}/cancel")
    void cancel(@PathVariable String externalOrderId,@RequestParam UUID brokerAccountId);
    record BrokerRequest(UUID executionIntentId,UUID executionAttemptId,String idempotencyKey,
                         UUID brokerAccountId,String instrument,String side,String orderType,
                         BigDecimal quantity,BigDecimal limitPrice){}
    record ReconcileRequest(UUID executionIntentId,UUID executionAttemptId,String idempotencyKey,
                            UUID brokerAccountId){}
    record BrokerResponse(String outcome,String externalOrderId,String correlationId,
                          String status,String reasonCode){}
}
