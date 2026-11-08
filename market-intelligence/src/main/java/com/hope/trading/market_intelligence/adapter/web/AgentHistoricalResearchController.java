package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.research.HistoricalDatasetExportApplicationService;
import com.hope.trading.market_intelligence.application.research.HistoricalDatasetExportRequest;
import com.hope.trading.market_intelligence.application.research.HistoricalDatasetExportResponse;
import com.hope.trading.market_intelligence.application.research.HistoricalReadinessRequest;
import com.hope.trading.market_intelligence.application.research.HistoricalReadinessResponse;
import com.hope.trading.market_intelligence.application.research.HistoricalReadinessApplicationService;
import com.hope.trading.market_intelligence.security.MiAgentPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intelligence/research")
public class AgentHistoricalResearchController {
    private final HistoricalReadinessApplicationService readiness;
    private final HistoricalDatasetExportApplicationService exports;

    public AgentHistoricalResearchController(HistoricalReadinessApplicationService readiness,
                                             HistoricalDatasetExportApplicationService exports) {
        this.readiness = readiness;
        this.exports = exports;
    }

    @PostMapping("/{scopeId}/readiness")
    public ResponseEntity<HistoricalReadinessResponse> inspect(
            @PathVariable UUID scopeId,
            @Valid @RequestBody HistoricalReadinessRequest request,
            Authentication authentication,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        MiAgentPrincipal agent = agent(authentication);
        if (!request.marketIds().isEmpty() && request.marketIds().size() > 2) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(readiness.inspect(agent(authentication), scopeId, request, correlationId));
    }

    @PostMapping("/{scopeId}/exports")
    public ResponseEntity<HistoricalDatasetExportResponse> requestExport(
            @PathVariable UUID scopeId,
            @Valid @RequestBody HistoricalDatasetExportRequest request,
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ResponseEntity.accepted().body(exports.submit(agent(authentication), scopeId, request, idempotencyKey, correlationId));
    }

    @GetMapping("/exports/{exportId}")
    public ResponseEntity<HistoricalDatasetExportResponse> status(
            @PathVariable UUID exportId,
            Authentication authentication,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ResponseEntity.ok(exports.status(agent(authentication), exportId, correlationId));
    }

    private MiAgentPrincipal agent(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof MiAgentPrincipal value)) {
            throw new IllegalStateException("AGENT_AUTHENTICATION_REQUIRED");
        }
        return value;
    }
}
