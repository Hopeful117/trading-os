package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.orchestration.IntelligenceOrchestrator;
import com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intelligence")
public class MarketIntelligenceController {
    private final IntelligenceOrchestrator orchestrator;

    public MarketIntelligenceController(IntelligenceOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/analyses")
    public ResponseEntity<ConsolidatedIntelligence> analyze(
            @Valid @RequestBody IntelligenceAnalysisRequestDto request
    ) {
        IntelligenceAnalysisRequest command = new IntelligenceAnalysisRequest(
                UUID.randomUUID(),
                request.marketId(),
                request.mode(),
                request.objective()
        );
        return ResponseEntity.ok(orchestrator.analyze(command));
    }
}
