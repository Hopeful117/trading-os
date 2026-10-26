package com.hope.trading.market_data.ctrader.authentication;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<CTraderAuthorizationService.AuthorizationResult> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        return ResponseEntity.ok(service.callback(code, state, error));
    }
}
