package com.hope.trading.market_data.ctrader.authentication;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/ctrader/authorization")
@ConditionalOnProperty(name = "ctrader.market-data.enabled", havingValue = "true")
public class CTraderAuthorizationController {
    private final CTraderAuthorizationService service;

    public CTraderAuthorizationController(CTraderAuthorizationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<CTraderAuthorizationService.AuthorizationStart> start() {
        return ResponseEntity.ok(service.start());
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        try {
            service.callback(code, state, error);
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .location(URI.create("/accounts?ctrader=market-data-completed")).build();
        } catch (CTraderAuthorizationException exception) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .location(URI.create("/accounts?ctrader=market-data-failed")).build();
        }
    }

    @GetMapping("/{attemptId}")
    public ResponseEntity<CTraderAuthorizationService.AuthorizationResult> status(
            @PathVariable java.util.UUID attemptId) {
        return ResponseEntity.ok(service.status(attemptId));
    }
}
