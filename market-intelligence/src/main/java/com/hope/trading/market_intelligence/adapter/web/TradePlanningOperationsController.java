package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.port.TradePlanningMetrics;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/internal/trade-planning")
public final class TradePlanningOperationsController {
    private final TradePlanningMetrics metrics;
    public TradePlanningOperationsController(TradePlanningMetrics metrics) {
        this.metrics = metrics;
    }
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Long>> metrics() {
        return ResponseEntity.ok(metrics.snapshot());
    }
}
