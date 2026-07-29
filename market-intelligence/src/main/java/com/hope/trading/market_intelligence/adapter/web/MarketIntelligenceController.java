package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intelligence")
public class MarketIntelligenceController {
    private final AnalysisExecutionService executions;

    public MarketIntelligenceController(AnalysisExecutionService executions) {
        this.executions = executions;
    }

    @PostMapping("/analyses")
    public ResponseEntity<AnalysisExecutionResponse> analyze(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @Valid @RequestBody IntelligenceAnalysisRequestDto request
    ) {
        UUID executionId = UUID.randomUUID();
        IntelligenceAnalysisRequest command = new IntelligenceAnalysisRequest(
                executionId,
                request.marketId(),
                request.mode(),
                request.objective()
        );
        AnalysisExecution execution = executions.create(
                command,
                new IdempotencyKey(idempotencyKey),
                requestId == null ? executionId.toString() : requestId,
                traceId == null ? executionId.toString() : traceId
        );
        return ResponseEntity.accepted()
                .location(URI.create(
                        "/api/v1/intelligence/analyses/" + execution.executionId()
                ))
                .body(AnalysisExecutionResponse.from(execution));
    }

    @GetMapping("/analyses/{executionId}")
    public ResponseEntity<AnalysisExecutionResponse> find(
            @PathVariable UUID executionId
    ) {
        return ResponseEntity.ok(
                AnalysisExecutionResponse.from(executions.find(executionId))
        );
    }

    @GetMapping("/analyses/{executionId}/result")
    public ResponseEntity<ConsolidatedIntelligence> result(
            @PathVariable UUID executionId
    ) {
        AnalysisExecution execution = executions.find(executionId);
        return execution.result()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.accepted().build());
    }

    @PostMapping("/analyses/{executionId}/cancel")
    public ResponseEntity<AnalysisExecutionResponse> cancel(
            @PathVariable UUID executionId
    ) {
        return ResponseEntity.ok(
                AnalysisExecutionResponse.from(executions.cancel(executionId))
        );
    }
}
