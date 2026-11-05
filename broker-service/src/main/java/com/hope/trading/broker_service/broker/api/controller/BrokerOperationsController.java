package com.hope.trading.broker_service.broker.api.controller;

import com.hope.trading.broker_service.broker.infrastructure.monitoring.BrokerOperationsMetrics;
import com.hope.trading.broker_service.ctrader.authentication.CTraderRequestSafetyGuard;
import java.util.Map;
import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/internal/v1/broker-operations")
public final class BrokerOperationsController {
    private final BrokerOperationsMetrics metrics;
    private final CTraderRequestSafetyGuard safety;

    public BrokerOperationsController(BrokerOperationsMetrics metrics, CTraderRequestSafetyGuard safety) {
        this.metrics = metrics;
        this.safety = safety;
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, BrokerOperationsMetrics.Snapshot>> metrics() {
        return ResponseEntity.ok(metrics.snapshot());
    }

    @GetMapping("/ctrader-request-budget")
    public ResponseEntity<CTraderRequestSafetyGuard.BudgetStatus> cTraderRequestBudget() {
        return ResponseEntity.ok(safety.budgetStatus());
    }
}
