package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.research.ResearchReadiness;
import com.hope.trading.market_intelligence.application.research.ResearchReadinessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/intelligence/research")
public class ResearchReadinessController {
    private final ResearchReadinessService readiness;

    public ResearchReadinessController(ResearchReadinessService readiness) {
        this.readiness = readiness;
    }

    @GetMapping("/readiness")
    public ResponseEntity<ResearchReadiness> readiness() {
        return ResponseEntity.ok(readiness.current());
    }
}
