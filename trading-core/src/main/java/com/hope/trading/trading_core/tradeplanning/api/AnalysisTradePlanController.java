package com.hope.trading.trading_core.tradeplanning.api;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.tradeplanning.application.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
/**
 * Public trade-plan creation entry point (STORY-0019).
 *
 * <p>Trading Core owns the user-facing orchestration: authenticated identity,
 * generation delegated to Market Intelligence over its internal API, then
 * risk evaluation. The path deliberately lives under this service's public
 * {@code /api/v1/trade-plans} namespace — it previously sat under
 * {@code /api/v1/intelligence/**}, which the Gateway routes to
 * market-intelligence, making it unreachable (WRONG_SERVICE).</p>
 */
@RequestMapping("/api/v1/trade-plans/analyses")
public class AnalysisTradePlanController {
    private final AnalysisTradePlanGenerationService service;
    public AnalysisTradePlanController(AnalysisTradePlanGenerationService service) {
        this.service = service;
    }

    @PostMapping("/{analysisExecutionId}/trade-plans")
    public ResponseEntity<AnalysisTradePlanGenerationService.Response> generate(
            @PathVariable UUID analysisExecutionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody Request request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDto user)) {
            throw new AnalysisTradePlanGenerationException(
                    "AUTHENTICATION_REQUIRED", "Authenticated user is required", 401);
        }
        return ResponseEntity.ok(service.generate(
                user.getUserId(), analysisExecutionId, request.accountId(), idempotencyKey));
    }

    public record Request(@NotNull UUID accountId) { }
}

@RestControllerAdvice(assignableTypes = AnalysisTradePlanController.class)
class AnalysisTradePlanGenerationExceptionHandler {
    @ExceptionHandler(AnalysisTradePlanGenerationException.class)
    ResponseEntity<Map<String, String>> handle(AnalysisTradePlanGenerationException failure) {
        return ResponseEntity.status(failure.status()).body(
                Map.of("code", failure.code(), "message", failure.getMessage()));
    }
}
