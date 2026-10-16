package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/intelligence/analyses")
public class InternalAnalysisExecutionController {
    private final AnalysisExecutionService executions;

    public InternalAnalysisExecutionController(AnalysisExecutionService executions) {
        this.executions = executions;
    }

    @PostMapping
    public ResponseEntity<AnalysisExecutionResponse> analyze(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 200) String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @Valid @RequestBody IntelligenceAnalysisRequestDto request
    ) {
        UUID executionId = UUID.randomUUID();
        IntelligenceAnalysisRequest command = new IntelligenceAnalysisRequest(
                executionId,
                request.marketId(),
                request.mode(),
                request.objective(),
                null,
                null,
                null,
                request.evaluationCutoff(),
                request.evidencePolicy()
        );
        AnalysisExecution execution = executions.create(
                command,
                new IdempotencyKey(idempotencyKey),
                requestId == null ? executionId.toString() : requestId,
                traceId == null ? executionId.toString() : traceId
        );
        return ResponseEntity.accepted()
                .location(URI.create(
                        "/internal/v1/intelligence/analyses/" + execution.executionId()))
                .body(AnalysisExecutionResponse.from(execution));
    }

    @GetMapping("/{executionId}")
    public ResponseEntity<AnalysisExecutionResponse> find(@PathVariable UUID executionId) {
        return ResponseEntity.ok(AnalysisExecutionResponse.from(executions.find(executionId)));
    }
}
