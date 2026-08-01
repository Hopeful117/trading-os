package com.hope.trading.broker_service.broker.api.controller;

import com.hope.trading.broker_service.broker.api.dto.BrokerApiDtos.*;
import com.hope.trading.broker_service.broker.application.service.BrokerOperationServices.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/internal/v1/executions")
public final class ExecutionController {
    private final ExecuteOrderService execution;private final ReconcileExecutionService reconciliation;private final CancelOrderService cancellation;
    public ExecutionController(ExecuteOrderService e,ReconcileExecutionService r,CancelOrderService c){execution=e;reconciliation=r;cancellation=c;}
    @PostMapping public ResponseEntity<BrokerApiResponse> execute(@Valid @RequestBody ExecutionApiRequest request){return ResponseEntity.ok(BrokerApiResponse.from(execution.execute(request.toModel())));}
    @PostMapping("/reconcile") public ResponseEntity<BrokerApiResponse> reconcile(@Valid @RequestBody ReconcileApiRequest request){return ResponseEntity.ok(BrokerApiResponse.from(reconciliation.reconcile(request.toModel())));}
    @PostMapping("/{externalOrderId}/cancel") public ResponseEntity<Void> cancel(@PathVariable String externalOrderId,@RequestParam UUID brokerAccountId){cancellation.cancel(brokerAccountId,externalOrderId);return ResponseEntity.noContent().build();}
}
