package com.hope.trading.trading_core.execution.application.research;

import com.hope.trading.trading_core.config.MarketIntelligenceFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "market-intelligence", contextId = "economicResearchOrchestrationClient",
        configuration = MarketIntelligenceFeignConfiguration.class)
public interface EconomicResearchMarketIntelligenceClient {
    @PostMapping("/internal/v1/research/economic/evaluate")
    EvaluationResponse evaluate(@RequestBody EvaluationRequest request);

    record EvaluationRequest(String candidateJsonl, String researchCutoff,
                             String transactionCostModelVersion, java.util.UUID brokerAccountId,
                             String provider, String productType) {
        public EvaluationRequest(String candidateJsonl, String researchCutoff, String transactionCostModelVersion) {
            this(candidateJsonl, researchCutoff, transactionCostModelVersion, null, null, null);
        }
    }

    record EvaluationResponse(String candidateJsonl, String resultJsonl, String manifestJson,
                              String readme, String manifestFingerprint) {
    }
}
