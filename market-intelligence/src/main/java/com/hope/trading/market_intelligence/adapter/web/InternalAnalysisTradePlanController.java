package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.pipeline.AnalysisTradePlanGenerationService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/intelligence/analyses")
public class InternalAnalysisTradePlanController {
    private final AnalysisTradePlanGenerationService service;

    public InternalAnalysisTradePlanController(AnalysisTradePlanGenerationService service) {
        this.service = service;
    }

    @PostMapping("/{analysisExecutionId}/trade-plans")
    public ResponseEntity<AnalysisTradePlanGenerationService.GenerationResponse> generate(
            @PathVariable UUID analysisExecutionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InternalAnalysisTradePlanRequest request,
            Authentication authentication) {
        UUID actorId = ((com.hope.trading.market_intelligence.security.MiServicePrincipal)
                authentication.getPrincipal()).requireMatchingActor(request.actorId());
        return ResponseEntity.ok(service.generate(analysisExecutionId, idempotencyKey,
                request.withActorId(actorId)));
    }
}
