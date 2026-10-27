package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.port.CapabilityExecutionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intelligence/analyses")
public class CapabilityExecutionController {
    private final CapabilityExecutionRepository executions;

    public CapabilityExecutionController(CapabilityExecutionRepository executions) {
        this.executions = executions;
    }

    @GetMapping("/{analysisExecutionId}/capabilities")
    public ResponseEntity<List<CapabilityExecutionResponse>> findByAnalysisExecutionId(
            @PathVariable UUID analysisExecutionId) {
        return ResponseEntity.ok(executions.findByAnalysisExecutionId(analysisExecutionId).stream()
                .map(CapabilityExecutionResponse::from)
                .toList());
    }
}
