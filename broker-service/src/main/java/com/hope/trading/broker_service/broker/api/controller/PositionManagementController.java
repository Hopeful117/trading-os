package com.hope.trading.broker_service.broker.api.controller;

import com.hope.trading.broker_service.broker.api.dto.PositionCloseApiDtos.*;
import com.hope.trading.broker_service.broker.application.service.BrokerOperationServices.*;
import com.hope.trading.broker_service.security.BrokerPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/internal/v1/positions")
public final class PositionManagementController {
    private final ResolveTargetService resolution;
    private final ExecuteCloseService execution;
    private final ReconcileCloseService reconciliation;

    public PositionManagementController(ResolveTargetService r, ExecuteCloseService e, ReconcileCloseService rec) {
        this.resolution = r; this.execution = e; this.reconciliation = rec;
    }

    @PostMapping("/resolve-target")
    public ResponseEntity<ResolvedTargetApiResponse> resolveTarget(@Valid @RequestBody ResolveTargetApiRequest request, @AuthenticationPrincipal BrokerPrincipal principal) {
        return ResponseEntity.ok(ResolvedTargetApiResponse.from(resolution.resolve(request.toModel(), principal.userId())));
    }

    @PostMapping("/execute-close")
    public ResponseEntity<BrokerCloseApiResponse> executeClose(@Valid @RequestBody ExecuteCloseApiRequest request, @AuthenticationPrincipal BrokerPrincipal principal) {
        return ResponseEntity.ok(BrokerCloseApiResponse.from(execution.execute(request.toModel(), principal.userId())));
    }

    @PostMapping("/reconcile-close")
    public ResponseEntity<ReconcileCloseApiResponse> reconcileClose(@Valid @RequestBody ReconcileCloseApiRequest request, @AuthenticationPrincipal BrokerPrincipal principal) {
        return ResponseEntity.ok(ReconcileCloseApiResponse.from(reconciliation.reconcile(request.toModel(), principal.userId())));
    }
}