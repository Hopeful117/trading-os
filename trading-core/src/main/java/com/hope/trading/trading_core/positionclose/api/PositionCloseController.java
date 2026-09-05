package com.hope.trading.trading_core.positionclose.api;

import com.hope.trading.trading_core.positionclose.api.dto.PositionCloseRequest;
import com.hope.trading.trading_core.positionclose.api.dto.PositionCloseResponse;
import com.hope.trading.trading_core.positionclose.application.service.PositionCloseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/positions/close")
public class PositionCloseController {
    private final PositionCloseService service;

    public PositionCloseController(PositionCloseService service) { this.service = service; }

    private static UUID principal(Authentication authentication) {
        return ((com.hope.trading.trading_core.dto.UserDto) authentication.getPrincipal()).getUserId();
    }

    @PostMapping
    public ResponseEntity<PositionCloseResponse> close(@PathVariable UUID accountId,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody PositionCloseRequest request,
            Authentication authentication) {
        UUID userId = principal(authentication);
        var command = service.close(userId, accountId, request.brokerPositionReference(), idempotencyKey);
        return ResponseEntity.accepted().body(toResponse(command));
    }

    @PostMapping("/{commandId}/reconcile")
    public ResponseEntity<PositionCloseResponse> reconcile(@PathVariable UUID accountId,
            @PathVariable UUID commandId,
            Authentication authentication) {
        UUID userId = principal(authentication);
        var command = service.reconcile(userId, commandId);
        return ResponseEntity.ok(toResponse(command));
    }

    private PositionCloseResponse toResponse(Object command) {
        // Use reflection to avoid compilation dependency on PositionCloseCommand
        try {
            var id = command.getClass().getField("id").get(command);
            var status = command.getClass().getField("status").get(command);
            var externalOrderId = command.getClass().getField("externalOrderId").get(command);
            var failureReason = command.getClass().getField("failureReason").get(command);
            var resolvedMutationScope = command.getClass().getField("resolvedMutationScope").get(command);
            var reconciliationResult = command.getClass().getField("reconciliationResult").get(command);
            
            return new PositionCloseResponse(
                    id.toString(),
                    status.toString(),
                    externalOrderId != null ? externalOrderId.toString() : null,
                    failureReason != null ? failureReason.toString() : null,
                    resolvedMutationScope != null ? resolvedMutationScope.toString() : null,
                    reconciliationResult != null ? reconciliationResult.toString() : null
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to extract command response", e);
        }
    }
}