package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.adapter.tradingcore.EconomicResearchClient;
import com.hope.trading.market_intelligence.application.research.EconomicResearchOrchestrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/internal/v1/research/economic")
public final class InternalEconomicResearchController {
    private final EconomicResearchOrchestrationService orchestration;

    public InternalEconomicResearchController(EconomicResearchOrchestrationService orchestration) {
        this.orchestration = orchestration;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<EconomicResearchClient.EvaluationResponse> evaluate(
            @RequestBody EvaluationRequest request) {
        if (request == null || request.candidateJsonl() == null || request.candidateJsonl().isBlank()
                || request.researchCutoff() == null || request.transactionCostModelVersion() == null
                || request.transactionCostModelVersion().isBlank()) {
            throw new IllegalArgumentException("candidateJsonl, researchCutoff and transactionCostModelVersion are required");
        }
        return ResponseEntity.ok(orchestration.evaluate(request.candidateJsonl(),
                Instant.parse(request.researchCutoff()), request.transactionCostModelVersion()));
    }

    public record EvaluationRequest(String candidateJsonl, String researchCutoff,
                                    String transactionCostModelVersion) {
    }
}
