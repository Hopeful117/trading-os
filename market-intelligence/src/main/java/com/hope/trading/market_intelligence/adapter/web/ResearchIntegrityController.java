package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.research.ResearchIntegrity;
import com.hope.trading.market_intelligence.application.research.ResearchIntegrityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/intelligence/research")
public class ResearchIntegrityController {
    private final ResearchIntegrityService integrity;

    public ResearchIntegrityController(ResearchIntegrityService integrity) {
        this.integrity = integrity;
    }

    @GetMapping("/integrity")
    public ResponseEntity<ResearchIntegrity> integrity() {
        return ResponseEntity.ok(integrity.current());
    }
}
