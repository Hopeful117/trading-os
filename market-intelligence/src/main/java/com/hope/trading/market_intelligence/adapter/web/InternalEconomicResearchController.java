package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.adapter.tradingcore.EconomicResearchClient;
import com.hope.trading.market_intelligence.application.research.EconomicResearchOrchestrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

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
        validateFeeContext(request.brokerAccountId(), request.provider(), request.productType());
        return ResponseEntity.ok(orchestration.evaluate(request.candidateJsonl(),
                Instant.parse(request.researchCutoff()), request.transactionCostModelVersion(),
                request.brokerAccountId(), request.provider(), request.productType()));
    }

    private void validateFeeContext(UUID brokerAccountId, String provider, String productType) {
        boolean any = brokerAccountId != null || provider != null || productType != null;
        boolean complete = brokerAccountId != null && provider != null && !provider.isBlank()
                && productType != null && !productType.isBlank();
        if (any && !complete) throw new IllegalArgumentException("brokerAccountId, provider and productType must be supplied together");
    }

    public record EvaluationRequest(String candidateJsonl, String researchCutoff,
                                    String transactionCostModelVersion, UUID brokerAccountId,
                                    String provider, String productType) {
        public EvaluationRequest(String candidateJsonl, String researchCutoff,
                                  String transactionCostModelVersion) {
            this(candidateJsonl, researchCutoff, transactionCostModelVersion, null, null, null);
        }
    }
}
