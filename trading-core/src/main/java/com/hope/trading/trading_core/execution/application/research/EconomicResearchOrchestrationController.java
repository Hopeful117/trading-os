package com.hope.trading.trading_core.execution.application.research;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/economic-research")
public final class EconomicResearchOrchestrationController {
    private final EconomicResearchMarketIntelligenceClient marketIntelligence;

    public EconomicResearchOrchestrationController(EconomicResearchMarketIntelligenceClient marketIntelligence) {
        this.marketIntelligence = marketIntelligence;
    }

    @PostMapping("/orchestrate")
    public ResponseEntity<EconomicResearchMarketIntelligenceClient.EvaluationResponse> orchestrate(
            @RequestBody OrchestrationRequest request) {
        if (request == null || request.candidateJsonl() == null || request.candidateJsonl().isBlank()
                || request.researchCutoff() == null || request.researchCutoff().isBlank()
                || request.transactionCostModelVersion() == null
                || request.transactionCostModelVersion().isBlank()) {
            throw new IllegalArgumentException("candidateJsonl, researchCutoff and transactionCostModelVersion are required");
        }
        boolean any = request.brokerAccountId() != null || request.provider() != null || request.productType() != null;
        boolean complete = request.brokerAccountId() != null && request.provider() != null
                && !request.provider().isBlank() && request.productType() != null
                && !request.productType().isBlank();
        if (any && !complete) {
            throw new IllegalArgumentException("brokerAccountId, provider and productType must be supplied together");
        }
        return ResponseEntity.ok(marketIntelligence.evaluate(new EconomicResearchMarketIntelligenceClient.EvaluationRequest(
                request.candidateJsonl(), request.researchCutoff(), request.transactionCostModelVersion(),
                request.brokerAccountId(), request.provider(), request.productType())));
    }

    public record OrchestrationRequest(String candidateJsonl, String researchCutoff,
                                       String transactionCostModelVersion, java.util.UUID brokerAccountId,
                                       String provider, String productType) {
        public OrchestrationRequest(String candidateJsonl, String researchCutoff, String transactionCostModelVersion) {
            this(candidateJsonl, researchCutoff, transactionCostModelVersion, null, null, null);
        }
    }
}
