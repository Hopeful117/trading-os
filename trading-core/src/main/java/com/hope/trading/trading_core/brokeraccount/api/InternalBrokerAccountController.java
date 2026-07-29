package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountService;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus;
import com.hope.trading.trading_core.dto.UserDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/broker-accounts")
@RequiredArgsConstructor
public class InternalBrokerAccountController {
    private final BrokerAccountService service;

    @GetMapping("/{accountId}")
    public ResponseEntity<BrokerAccountResponse> owned(@PathVariable UUID accountId, Authentication authentication) {
        UserDto principal = (UserDto) authentication.getPrincipal();
        return ResponseEntity.ok(service.get(principal.getUserId(), accountId));
    }

    @PostMapping("/{accountId}/connection-status")
    public ResponseEntity<BrokerAccountResponse> update(@PathVariable UUID accountId,
                                                        @Valid @RequestBody ConnectionUpdate request,
                                                        Authentication authentication) {
        UserDto principal = (UserDto) authentication.getPrincipal();
        return ResponseEntity.ok(service.updateStatus(principal.getUserId(), accountId, request.status(),
                request.credentialReference(), request.externalAccountId(), request.validatedAt()));
    }

    public record ConnectionUpdate(@NotNull BrokerConnectionStatus status, UUID credentialReference,
                                   String externalAccountId, @NotNull Instant validatedAt) {
    }
}
