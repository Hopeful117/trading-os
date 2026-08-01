package com.hope.trading.trading_core.risk.api;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.risk.application.RiskEvaluationException;
import com.hope.trading.trading_core.risk.application.RiskEvaluationModels.Command;
import com.hope.trading.trading_core.risk.application.RiskEvaluationModels.Response;
import com.hope.trading.trading_core.risk.application.TradePlanRiskEvaluationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RequestMapping("/api/v1/trade-plans/{tradePlanId}/versions/{version}/risk-evaluations")
public class TradePlanRiskEvaluationController {
    private final TradePlanRiskEvaluationService service;
    private final Clock clock;

    public TradePlanRiskEvaluationController(TradePlanRiskEvaluationService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<Response> evaluate(@PathVariable UUID tradePlanId, @PathVariable long version,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                             @Valid @RequestBody Request request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDto user)) {
            throw new RiskEvaluationException("AUTHENTICATION_REQUIRED", "Authenticated user is required", 401);
        }
        return ResponseEntity.ok(service.evaluate(new Command(user.getUserId(), tradePlanId, version,
                request.accountId(), idempotencyKey, clock.instant())));
    }

    public record Request(@NotNull UUID accountId) { }
}

@RestControllerAdvice(assignableTypes = TradePlanRiskEvaluationController.class)
class TradePlanRiskEvaluationExceptionHandler {
    @ExceptionHandler(RiskEvaluationException.class)
    ResponseEntity<Map<String, String>> command(RiskEvaluationException failure) {
        return ResponseEntity.status(failure.status()).body(Map.of("code", failure.code(), "message", failure.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<Map<String, String>> invalid(Exception failure) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("code", "INVALID_COMMAND", "message", failure.getMessage()));
    }
}
