package com.hope.trading.trading_core.tradeplanning.api;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.tradeplanning.application.ManualTradePlanException;
import com.hope.trading.trading_core.tradeplanning.application.ManualTradePlanOrchestrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestController
@RequestMapping("/api/v1/trade-plans")
public final class ManualTradePlanController {
    private final ManualTradePlanOrchestrationService orchestration;

    public ManualTradePlanController(ManualTradePlanOrchestrationService orchestration) {
        this.orchestration = orchestration;
    }

    @PostMapping("/manual")
    public ResponseEntity<OpportunityTradePlanController.Response> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ManualTradePlanRequest request,
            Authentication authentication) {
        UserDto user = authenticated(authentication);
        var response = orchestration.create(user.getUserId(), request, idempotencyKey);
        return ResponseEntity.status(201).body(
                new OpportunityTradePlanController.Response(response.tradePlanId(), response.tradePlanVersion()));
    }

    private static UserDto authenticated(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDto user)) {
            throw new ManualTradePlanException("AUTHENTICATION_REQUIRED",
                    "Authenticated user is required", 401);
        }
        return user;
    }
}

@RestControllerAdvice(assignableTypes = ManualTradePlanController.class)
class ManualTradePlanExceptionHandler {
    @ExceptionHandler(ManualTradePlanException.class)
    ResponseEntity<Map<String, String>> command(ManualTradePlanException failure) {
        return ResponseEntity.status(failure.status())
                .body(Map.of("code", failure.code(), "message", failure.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException failure) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", "INVALID_MANUAL_TRADE",
                        "message", failure.getMessage()));
    }
}
