package com.hope.trading.broker_service.ctrader.authentication;

import com.hope.trading.broker_service.security.BrokerPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/broker-accounts")
public class CTraderAuthenticationController {
    private final CTraderAuthenticationService service;

    public CTraderAuthenticationController(CTraderAuthenticationService service) {
        this.service = service;
    }

    @PostMapping("/{accountId}/ctrader/authorization")
    public ResponseEntity<CTraderAuthorizationResponse> initiate(@PathVariable UUID accountId,
                                                                  @RequestHeader("Authorization") String authorization,
                                                                  Authentication authentication) {
        return ResponseEntity.ok(service.initiate(principal(authentication).userId(), accountId, authorization));
    }

    @GetMapping("/ctrader/authorization/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String state,
                                                                   @RequestParam(required = false) String code,
                                                                   @RequestParam(required = false) String error) {
        try {
            service.callback(state, code, error);
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .location(URI.create("/accounts?ctrader=completed")).build();
        } catch (CTraderAuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .location(URI.create("/accounts?ctrader=failed")).build();
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .location(URI.create("/accounts?ctrader=failed")).build();
        }
    }

    @GetMapping("/{accountId}/ctrader/authorization/{attemptId}")
    public ResponseEntity<CTraderAuthorizationResponse> get(@PathVariable UUID accountId,
                                                             @PathVariable UUID attemptId,
                                                             Authentication authentication) {
        CTraderAuthorizationResponse response = service.get(principal(authentication).userId(), accountId, attemptId);
        return response.attemptId().equals(attemptId) ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
    }

    @PostMapping("/{accountId}/ctrader/authorization/{attemptId}/binding")
    public ResponseEntity<CTraderAuthorizationResponse> bind(@PathVariable UUID accountId,
                                                              @PathVariable UUID attemptId,
                                                              @Valid @RequestBody CTraderBindAccountRequest request,
                                                              @RequestHeader("Authorization") String authorization,
                                                              Authentication authentication) {
        return ResponseEntity.ok(service.bind(principal(authentication).userId(), accountId, attemptId,
                request.externalAccountId(), authorization));
    }

    private BrokerPrincipal principal(Authentication authentication) {
        return (BrokerPrincipal) authentication.getPrincipal();
    }
}
