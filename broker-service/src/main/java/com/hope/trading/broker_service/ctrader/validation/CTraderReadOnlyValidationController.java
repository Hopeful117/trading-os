package com.hope.trading.broker_service.ctrader.validation;

import com.hope.trading.broker_service.ctrader.authentication.CTraderOpenApiClient;
import com.hope.trading.broker_service.ctrader.execution.CTraderExecutionInstrumentContract;
import com.hope.trading.broker_service.ctrader.metadata.CTraderInstrumentMetadata;
import com.hope.trading.broker_service.security.BrokerPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/broker-accounts")
public final class CTraderReadOnlyValidationController {
    private final CTraderReadOnlyValidationService service;

    public CTraderReadOnlyValidationController(CTraderReadOnlyValidationService service) {
        this.service = service;
    }

    @GetMapping("/{id}/ctrader/instrument")
    public ResponseEntity<CTraderExecutionInstrumentContract> instrument(
            @PathVariable UUID id,
            @RequestParam String instrument,
            @AuthenticationPrincipal BrokerPrincipal principal) {
        return ResponseEntity.ok(service.instrument(actor(principal), id, instrument));
    }

    @GetMapping("/{id}/ctrader/quote")
    public ResponseEntity<CTraderOpenApiClient.CTraderQuote> quote(
            @PathVariable UUID id,
            @RequestParam String instrument,
            @AuthenticationPrincipal BrokerPrincipal principal) {
        return ResponseEntity.ok(service.quote(actor(principal), id, instrument));
    }

    @GetMapping("/{id}/ctrader/instrument/metadata")
    public ResponseEntity<CTraderInstrumentMetadata> metadata(
            @PathVariable UUID id,
            @RequestParam String instrument,
            @AuthenticationPrincipal BrokerPrincipal principal) {
        return ResponseEntity.ok(service.metadata(actor(principal), id, instrument));
    }

    private UUID actor(BrokerPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new IllegalArgumentException("Delegated actor is required");
        }
        return principal.userId();
    }
}
