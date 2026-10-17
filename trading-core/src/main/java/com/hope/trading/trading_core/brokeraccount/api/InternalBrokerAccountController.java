package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountService;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus;
import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.security.ServicePrincipal;
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
        return ResponseEntity.ok(service.get(ownerId(authentication), accountId));
    }

    @PostMapping("/{accountId}/connection-status")
    public ResponseEntity<BrokerAccountResponse> update(@PathVariable UUID accountId,
                                                        @Valid @RequestBody ConnectionUpdate request,
                                                        Authentication authentication) {
        return ResponseEntity.ok(service.updateStatus(ownerId(authentication), accountId, request.status(),
                request.credentialReference(), request.externalAccountId(), request.validatedAt()));
    }

    private UUID ownerId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDto user) return user.getUserId();
        if (principal instanceof ServicePrincipal service && service.actorId() != null) return service.actorId();
        throw new IllegalStateException("Authenticated principal has no user actor");
    }

    public record ConnectionUpdate(@NotNull BrokerConnectionStatus status, UUID credentialReference,
                                   String externalAccountId, @NotNull Instant validatedAt) {
    }
}
