package com.hope.trading.market_intelligence.adapter.tradingcore;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "trading-core", contextId = "economicResearchClient",
        configuration = EconomicResearchFeignConfiguration.class)
public interface EconomicResearchClient {
    @PostMapping("/internal/v1/economic-research/evaluate")
    EvaluationResponse evaluate(@RequestBody EvaluationRequest request);

    record EvaluationRequest(String candidateJsonl, String researchCutoff,
                             String transactionCostModelVersion, java.util.UUID brokerAccountId,
                             String provider, String productType) {
        public EvaluationRequest(String candidateJsonl, String researchCutoff,
                                  String transactionCostModelVersion) {
            this(candidateJsonl, researchCutoff, transactionCostModelVersion, null, null, null);
        }
    }

    record EvaluationResponse(String candidateJsonl, String resultJsonl, String manifestJson,
                              String readme, String manifestFingerprint) { }
}
