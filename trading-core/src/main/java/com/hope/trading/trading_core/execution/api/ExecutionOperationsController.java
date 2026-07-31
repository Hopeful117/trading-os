package com.hope.trading.trading_core.execution.api;

import com.hope.trading.trading_core.execution.infrastructure.observability.ExecutionOperationsMetrics;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/executions")
public class ExecutionOperationsController {
    private final ExecutionOperationsMetrics metrics;
    public ExecutionOperationsController(ExecutionOperationsMetrics metrics){this.metrics=metrics;}
    @GetMapping("/metrics")
    public ResponseEntity<ExecutionOperationsMetrics.Snapshot> metrics(){
        return ResponseEntity.ok(metrics.snapshot());
    }
}
