package com.hope.trading.market_intelligence.adapter.ai;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@FeignClient(name = "ai-engine", url = "${ai.engine.base-url}",
        configuration = AiEngineFeignConfiguration.class)
interface AiEngineClient {
    @PostMapping("/internal/v1/analyses")
    AiEngineAcceptedResponse submit(@RequestBody AiEngineAnalysisRequest request);

    @GetMapping("/internal/v1/analyses/{executionId}")
    AiEngineProgressResponse progress(@PathVariable UUID executionId);

    @GetMapping("/internal/v1/analyses/{executionId}/result")
    AiEngineResultResponse result(@PathVariable UUID executionId);

    @PostMapping("/internal/v1/analyses/{executionId}/cancel")
    void cancel(@PathVariable UUID executionId);
}
