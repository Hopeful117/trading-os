package com.hope.trading.trading_core.tradeplanning.api;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.tradeplanning.application.TradePlanningProfileException;
import com.hope.trading.trading_core.tradeplanning.application.TradePlanningProfileService;
import com.hope.trading.trading_core.tradeplanning.application.TradePlanningProfileService.Values;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trade-planning-profiles")
public class TradePlanningProfileController {
    private final TradePlanningProfileService service;

    @PostMapping
    public ResponseEntity<TradePlanningProfile> create(@Valid @RequestBody ProfileRequest request,
                                                        Authentication authentication) {
        TradePlanningProfile result = service.create(principal(authentication), request.values());
        return ResponseEntity.created(URI.create("/api/v1/trade-planning-profiles/" + result.id()
                + "/versions/" + result.version())).body(result);
    }
    @PostMapping("/{profileId}/versions")
    public ResponseEntity<TradePlanningProfile> createVersion(@PathVariable UUID profileId,
            @Valid @RequestBody ProfileRequest request, Authentication authentication) {
        return ResponseEntity.ok(service.createVersion(principal(authentication), profileId, request.values()));
    }
    @PutMapping("/accounts/{accountId}/assignment")
    public ResponseEntity<TradePlanningProfile> assign(@PathVariable UUID accountId,
            @Valid @RequestBody AssignmentRequest request, Authentication authentication) {
        return ResponseEntity.ok(service.assign(principal(authentication), accountId,
                request.profileId(), request.profileVersion()));
    }
    @GetMapping("/accounts/{accountId}/effective")
    public ResponseEntity<TradePlanningProfile> effective(@PathVariable UUID accountId, Authentication authentication) {
        return ResponseEntity.ok(service.effective(principal(authentication), accountId));
    }
    private UUID principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDto user)) {
            throw new TradePlanningProfileException("AUTHENTICATION_REQUIRED", "Authenticated user is required", 401);
        }
        return user.getUserId();
    }

    public record ProfileRequest(@NotNull @Positive BigDecimal riskBudgetAmount, @NotBlank String currency,
            @NotNull TradePlanningProfile.EntryType entryType,
            @NotNull TradePlanningProfile.StopStrategy stopStrategy,
            @NotNull @Positive BigDecimal stopDistancePercent,
            @NotNull TradePlanningProfile.TargetStrategy targetStrategy,
            @NotNull @Positive BigDecimal targetRiskMultiple,
            @NotNull TradePlanningProfile.PlanningHorizon horizon,
            @NotNull Duration validity) {
        Values values() { return new Values(riskBudgetAmount, currency, entryType, stopStrategy,
                stopDistancePercent, targetStrategy, targetRiskMultiple, horizon, validity); }
    }
    public record AssignmentRequest(@NotNull UUID profileId, @Positive long profileVersion) { }
}

@RestControllerAdvice(assignableTypes = TradePlanningProfileController.class)
class TradePlanningProfileExceptionHandler {
    @ExceptionHandler(TradePlanningProfileException.class)
    ResponseEntity<Map<String, String>> handle(TradePlanningProfileException failure) {
        return ResponseEntity.status(failure.status()).body(Map.of("code", failure.code(), "message", failure.getMessage()));
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, String>> invalid(Exception failure) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_PLANNING_PROFILE", "message", failure.getMessage()));
    }
}
