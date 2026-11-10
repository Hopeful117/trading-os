package com.hope.trading.broker_service.ctrader.validation;

import com.hope.trading.broker_service.security.BrokerPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Public authenticated facade for the owner's read-only cTrader validation. */
@RestController
@RequestMapping("/api/v1/broker-accounts")
public final class CTraderReadOnlyValidationApiController {
    private final CTraderReadOnlyValidationService service;

    public CTraderReadOnlyValidationApiController(CTraderReadOnlyValidationService service) {
        this.service = service;
    }

    @GetMapping("/{id}/ctrader/validation")
    public ResponseEntity<CTraderReadOnlyValidation> validation(
            @PathVariable UUID id,
            @RequestParam String instrument,
            @AuthenticationPrincipal BrokerPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new IllegalArgumentException("Authenticated owner is required");
        }
        return ResponseEntity.ok(service.validation(principal.userId(), id, instrument));
    }
}
