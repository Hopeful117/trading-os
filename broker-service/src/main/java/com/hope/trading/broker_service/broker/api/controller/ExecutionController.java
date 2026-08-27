package com.hope.trading.broker_service.broker.api.controller;

import com.hope.trading.broker_service.broker.api.dto.BrokerApiDtos.*;
import com.hope.trading.broker_service.broker.application.service.BrokerOperationServices.*;
import com.hope.trading.broker_service.security.BrokerPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/internal/v1/executions")
public final class ExecutionController {
    private final ExecuteOrderService execution;private final ReconcileExecutionService reconciliation;private final CancelOrderService cancellation;
    public ExecutionController(ExecuteOrderService e,ReconcileExecutionService r,CancelOrderService c){execution=e;reconciliation=r;cancellation=c;}
    @PostMapping public ResponseEntity<BrokerApiResponse> execute(@Valid @RequestBody ExecutionApiRequest request,@AuthenticationPrincipal BrokerPrincipal principal){return ResponseEntity.ok(BrokerApiResponse.from(execution.execute(request.toModel(),principal.userId())));}
    @PostMapping("/reconcile") public ResponseEntity<BrokerApiResponse> reconcile(@Valid @RequestBody ReconcileApiRequest request,@AuthenticationPrincipal BrokerPrincipal principal){return ResponseEntity.ok(BrokerApiResponse.from(reconciliation.reconcile(request.toModel(),principal.userId())));}
    @PostMapping("/{externalOrderId}/cancel") public ResponseEntity<Void> cancel(@PathVariable String externalOrderId,@RequestParam UUID brokerAccountId,@AuthenticationPrincipal BrokerPrincipal principal){cancellation.cancel(brokerAccountId,externalOrderId,principal.userId());return ResponseEntity.noContent().build();}
}
