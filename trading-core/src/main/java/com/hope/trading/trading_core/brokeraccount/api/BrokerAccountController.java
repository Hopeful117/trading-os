package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountService;
import com.hope.trading.trading_core.brokeraccount.application.BrokerConnectionCommandClient;
import com.hope.trading.trading_core.dto.UserDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/broker-accounts")
@RequiredArgsConstructor
public class BrokerAccountController {
    private final BrokerAccountService service;
    private final BrokerConnectionCommandClient brokerConnection;

    @PostMapping
    public ResponseEntity<BrokerAccountResponse> create(@Valid @RequestBody CreateBrokerAccountRequest request,
                                                        Authentication authentication) {
        BrokerAccountResponse response = service.create(principal(authentication).getUserId(), request);
        return ResponseEntity.created(URI.create("/api/v1/broker-accounts/" + response.id())).body(response);
    }

    @GetMapping
    public ResponseEntity<List<BrokerAccountResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(service.list(principal(authentication).getUserId()));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<BrokerAccountResponse> get(@PathVariable UUID accountId, Authentication authentication) {
        return ResponseEntity.ok(service.get(principal(authentication).getUserId(), accountId));
    }

    @GetMapping("/{accountId}/status")
    public ResponseEntity<BrokerAccountResponse> status(@PathVariable UUID accountId, Authentication authentication) {
        return get(accountId, authentication);
    }

    @PostMapping("/{accountId}/disconnect")
    public ResponseEntity<BrokerAccountResponse> disconnect(@PathVariable UUID accountId,
                                                            Authentication authentication) {
        brokerConnection.disconnect(accountId);
        return ResponseEntity.ok(service.get(principal(authentication).getUserId(), accountId));
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID accountId, Authentication authentication) {
        brokerConnection.revoke(accountId);
        return ResponseEntity.noContent().build();
    }

    private UserDto principal(Authentication authentication) {
        return (UserDto) authentication.getPrincipal();
    }
}
