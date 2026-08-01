package com.hope.trading.broker_service.broker.api.controller;

import com.hope.trading.broker_service.broker.infrastructure.monitoring.BrokerOperationsMetrics;
import java.util.Map;
import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/internal/v1/broker-operations")
public final class BrokerOperationsController {private final BrokerOperationsMetrics metrics;public BrokerOperationsController(BrokerOperationsMetrics m){metrics=m;}@GetMapping("/metrics")public ResponseEntity<Map<String,BrokerOperationsMetrics.Snapshot>> metrics(){return ResponseEntity.ok(metrics.snapshot());}}
