package com.hope.trading.trading_core.risk.api;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.risk.application.RiskEvaluationException;
import com.hope.trading.trading_core.risk.application.TradeScenarioProjectionResponse;
import com.hope.trading.trading_core.risk.application.TradeScenarioProjectionService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RequestMapping("/api/v1/trade-plans/{tradePlanId}/versions/{version}/projection")
public final class TradeScenarioProjectionController {
    private final TradeScenarioProjectionService service;

    public TradeScenarioProjectionController(TradeScenarioProjectionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<TradeScenarioProjectionResponse> project(
            @PathVariable UUID tradePlanId, @PathVariable long version, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDto user)) {
            throw new RiskEvaluationException("AUTHENTICATION_REQUIRED", "Authenticated user is required", 401);
        }
        return ResponseEntity.ok(service.project(user.getUserId(), tradePlanId, version));
    }
}

@RestControllerAdvice(assignableTypes = TradeScenarioProjectionController.class)
class TradeScenarioProjectionExceptionHandler {
    @ExceptionHandler(RiskEvaluationException.class)
    ResponseEntity<Map<String, String>> command(RiskEvaluationException failure) {
        return ResponseEntity.status(failure.status())
                .body(Map.of("code", failure.code(), "message", failure.getMessage()));
    }
}
