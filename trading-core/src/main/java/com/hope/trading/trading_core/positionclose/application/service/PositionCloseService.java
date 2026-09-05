package com.hope.trading.trading_core.positionclose.application.service;

import com.hope.trading.trading_core.positionclose.application.port.BrokerPositionClosePort;
import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseStatus;
import com.hope.trading.trading_core.positionclose.domain.model.ReconciliationCloseResult;
import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseCommand;
import com.hope.trading.trading_core.positionclose.domain.repository.PositionCloseCommandRepositoryPort;
import com.hope.trading.trading_core.positionclose.domain.service.PositionCloseLifecycleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PositionCloseService {
    private final PositionCloseCommandRepositoryPort repository;
    private final BrokerPositionClosePort brokerPort;

    public PositionCloseService(PositionCloseCommandRepositoryPort repository, BrokerPositionClosePort brokerPort) {
        this.repository = repository; this.brokerPort = brokerPort;
    }

    @Transactional
    public PositionCloseCommand close(UUID userId, UUID accountId, String brokerPositionReference, String idempotencyKey) {
        Optional<PositionCloseCommand> existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        var resolveResponse = brokerPort.resolveTarget(accountId, brokerPositionReference);
        String resolvedMutationScope = resolveResponse.resolvedMutationScope();

        PositionCloseCommand command = PositionCloseLifecycleService.create(UUID.randomUUID(), accountId, resolveResponse.brokerAccountId(),
                brokerPositionReference, resolvedMutationScope, idempotencyKey, Instant.now());

        List<PositionCloseCommand> activeCommands = repository.findActiveByBrokerAccountAndScope(
                command.brokerAccountId, command.resolvedMutationScope);
        if (!activeCommands.isEmpty()) {
            throw new PositionCloseConflictException("Active close command already exists for this scope");
        }

        command = repository.save(command);

        try {
            command = PositionCloseLifecycleService.transitionToSubmitted(command, Instant.now());
            command = repository.save(command);

            var executeResponse = brokerPort.executeClose(resolvedMutationScope, idempotencyKey);
            command = mapBrokerResult(command, executeResponse, Instant.now());
            command = repository.save(command);
            return command;
        } catch (Exception e) {
            command = PositionCloseLifecycleService.transitionToUnknown(command, e.getClass().getSimpleName().replace("Exception", "").toUpperCase(), Instant.now());
            return repository.save(command);
        }
    }

    @Transactional
    public PositionCloseCommand reconcile(UUID userId, UUID commandId) {
        PositionCloseCommand command = repository.findById(commandId)
                .orElseThrow(() -> new PositionCloseCommandNotFoundException("Command not found: " + commandId));

        if (!command.accountId.equals(userId)) {
            throw new PositionCloseAuthorizationException("Command does not belong to user");
        }

        if (!command.isReconcilable()) {
            throw new PositionCloseConflictException("Command is not in a reconcilable state: " + command.status);
        }

        var reconcileResponse = brokerPort.reconcileClose(command.resolvedMutationScope, command.idempotencyKey);
        ReconciliationCloseResult result = mapReconciliationResult(reconcileResponse);
        command = PositionCloseLifecycleService.reconcile(command, result, Instant.now());
        return repository.save(command);
    }

    private PositionCloseCommand mapBrokerResult(PositionCloseCommand command, BrokerPositionClosePort.BrokerCloseResponse response, Instant now) {
        return switch (response.outcome()) {
            case "ACKNOWLEDGED" -> PositionCloseLifecycleService.transitionToAcknowledged(command, response.externalOrderId(), now);
            case "REJECTED" -> PositionCloseLifecycleService.transitionToRejected(command, response.reasonCode(), now);
            case "UNKNOWN" -> PositionCloseLifecycleService.transitionToUnknown(command, response.reasonCode(), now);
            default -> PositionCloseLifecycleService.transitionToUnknown(command, "UNEXPECTED_OUTCOME", now);
        };
    }

    private ReconciliationCloseResult mapReconciliationResult(BrokerPositionClosePort.BrokerReconcileResponse response) {
        return switch (response.reconciliationResult()) {
            case "EXPOSURE_CONFIRMED_ABSENT" -> ReconciliationCloseResult.EXPOSURE_CONFIRMED_ABSENT;
            case "COMMAND_CONFIRMED_NOT_EXECUTED" -> ReconciliationCloseResult.COMMAND_CONFIRMED_NOT_EXECUTED;
            default -> ReconciliationCloseResult.RECONCILIATION_INCONCLUSIVE;
        };
    }

    public record ResolveTargetResponse(UUID brokerAccountId, String resolvedMutationScope) {}
}

class PositionCloseCommandNotFoundException extends RuntimeException { public PositionCloseCommandNotFoundException(String m){super(m);} }
class PositionCloseConflictException extends RuntimeException { public PositionCloseConflictException(String m){super(m);} }
class PositionCloseAuthorizationException extends RuntimeException { public PositionCloseAuthorizationException(String m){super(m);} }