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
@RequestMapping("/api/v1/intelligence/analyses")
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
