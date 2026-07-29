package com.hope.trading.broker_service.connection.api;

import com.hope.trading.broker_service.connection.application.BrokerCredentialConnectionService;
import com.hope.trading.broker_service.connection.application.CredentialCommandRateLimiter;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.security.BrokerPrincipal;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/broker-accounts/{accountId}")
@ConditionalOnProperty(name = "trading-os.broker.credentials.source", havingValue = "stored")
public class BrokerCredentialController {
    private final BrokerCredentialConnectionService service;
    private final CredentialCommandRateLimiter rateLimiter;

    public BrokerCredentialController(BrokerCredentialConnectionService service,
                                      CredentialCommandRateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/credentials")
    public ResponseEntity<CredentialValidationResponse> connect(@PathVariable UUID accountId,
                                                               @Valid @RequestBody SubmitBrokerCredentialsRequest request,
                                                               @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                               Authentication authentication) {
        try (CredentialMaterial material = material(request)) {
            BrokerPrincipal principal = principal(authentication);
            rateLimiter.check(principal.userId(), accountId);
            return ResponseEntity.ok(service.connect(principal.userId(), accountId,
                    material, authorization));
        }
    }

    @PutMapping("/credentials")
    public ResponseEntity<CredentialValidationResponse> rotate(@PathVariable UUID accountId,
                                                              @Valid @RequestBody SubmitBrokerCredentialsRequest request,
                                                              @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                              Authentication authentication) {
        try (CredentialMaterial material = material(request)) {
            BrokerPrincipal principal = principal(authentication);
            rateLimiter.check(principal.userId(), accountId);
            return ResponseEntity.ok(service.rotate(principal.userId(), accountId,
                    material, authorization));
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<CredentialValidationResponse> validate(@PathVariable UUID accountId,
                                                                @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                                Authentication authentication) {
        BrokerPrincipal principal = principal(authentication);
        rateLimiter.check(principal.userId(), accountId);
        return ResponseEntity.ok(service.validate(principal.userId(), accountId, authorization));
    }

    @GetMapping("/connection-status")
    public ResponseEntity<BrokerConnectionResponse> status(@PathVariable UUID accountId,
                                                          @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                          Authentication authentication) {
        return ResponseEntity.ok(service.get(principal(authentication).userId(), accountId, authorization));
    }

    @DeleteMapping("/credentials")
    public ResponseEntity<Void> revoke(@PathVariable UUID accountId,
                                       @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                       Authentication authentication) {
        service.revoke(principal(authentication).userId(), accountId, authorization);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/technical-disconnect")
    public ResponseEntity<Void> disconnect(@PathVariable UUID accountId,
                                           @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                           Authentication authentication) {
        service.disconnect(principal(authentication).userId(), accountId, authorization);
        return ResponseEntity.noContent().build();
    }

    private CredentialMaterial material(SubmitBrokerCredentialsRequest request) {
        return new CredentialMaterial(request.apiKey().toCharArray(), request.apiSecret().toCharArray(),
                request.passphrase() == null ? null : request.passphrase().toCharArray());
    }

    private BrokerPrincipal principal(Authentication authentication) {
        return (BrokerPrincipal) authentication.getPrincipal();
    }
}
