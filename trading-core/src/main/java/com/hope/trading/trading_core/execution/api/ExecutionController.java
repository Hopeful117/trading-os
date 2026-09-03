package com.hope.trading.trading_core.execution.api;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.execution.api.dto.*;
import com.hope.trading.trading_core.execution.application.command.ValidateAndCreateCommand;
import com.hope.trading.trading_core.execution.application.service.*;
import com.hope.trading.trading_core.execution.domain.model.*;
import com.hope.trading.trading_core.execution.domain.repository.*;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/executions")
public class ExecutionController {
    private final ValidateAndCreateService validation;
    private final ExecuteTradeService execution;
    private final RetryExecutionService retry;
    private final CancelExecutionService cancellation;
    private final RecoverExecutionService recovery;
    private final QueryExecutionService query;
    private final BrokerOrderRepositoryPort orders;
    private final ExecutionAttemptRepositoryPort attempts;
    public ExecutionController(ValidateAndCreateService validation, ExecuteTradeService execution,
            RetryExecutionService retry, CancelExecutionService cancellation,
            RecoverExecutionService recovery, QueryExecutionService query,
            BrokerOrderRepositoryPort orders, ExecutionAttemptRepositoryPort attempts){
        this.validation=validation;this.execution=execution;this.retry=retry;
        this.cancellation=cancellation;this.recovery=recovery;this.query=query;
        this.orders=orders;this.attempts=attempts;
    }
    @PostMapping("/validate")
    public ResponseEntity<ExecutionDto> validateAndCreate(
            @Valid @RequestBody ValidateAndCreateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication){
        var intent=validation.validateAndCreate(
                new ValidateAndCreateCommand(
                        principal(authentication).getUserId(),
                        request.tradePlanId(), request.tradePlanVersion(),
                        request.evaluationId(), request.brokerAccountId(),
                        new IdempotencyKey(idempotencyKey),
                        request.expiresAt()));
        return ResponseEntity.created(URI.create("/executions/"+intent.id().value()))
                .body(ExecutionDto.from(intent));
    }
    @PostMapping("/{id}/execute") public ResponseEntity<ExecutionDto> execute(
            @PathVariable java.util.UUID id,Authentication authentication){
        var intent=requireOwned(id,authentication);
        var executed=execution.execute(new ExecutionIntentId(id));
        return ResponseEntity.ok(toEnrichedDto(executed));
    }
    @GetMapping("/{id}") public ResponseEntity<ExecutionDto> get(
            @PathVariable java.util.UUID id,Authentication authentication){
        return ResponseEntity.ok(toEnrichedDto(requireOwned(id,authentication)));
    }
    @GetMapping public ResponseEntity<List<ExecutionSummaryDto>> list(Authentication authentication){
        return ResponseEntity.ok(query.findOwned(principal(authentication).getUserId())
                .stream().map(ExecutionSummaryDto::from).toList());
    }
    @PostMapping("/{id}/retry") public ResponseEntity<ExecutionDto> retry(
            @PathVariable java.util.UUID id,Authentication authentication){
        requireOwned(id,authentication);
        var retried=retry.retry(new ExecutionIntentId(id));
        return ResponseEntity.ok(toEnrichedDto(retried));
    }
    @PostMapping("/{id}/cancel") public ResponseEntity<ExecutionDto> cancel(
            @PathVariable java.util.UUID id,Authentication authentication){
        requireOwned(id,authentication);
        var cancelled=cancellation.cancel(new ExecutionIntentId(id));
        return ResponseEntity.ok(toEnrichedDto(cancelled));
    }
    @PostMapping("/{id}/reconcile") public ResponseEntity<ExecutionDto> reconcile(
            @PathVariable java.util.UUID id,Authentication authentication){
        requireOwned(id,authentication);
        var recovered=recovery.recoverOne(new ExecutionIntentId(id));
        return ResponseEntity.ok(toEnrichedDto(recovered));
    }
    @PostMapping("/recovery") public ResponseEntity<List<ExecutionDto>> recover(){
        return ResponseEntity.ok(recovery.recoverAll().stream()
                .map(this::toEnrichedDto).toList());
    }
    private ExecutionDto toEnrichedDto(
            com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent intent){
        return ExecutionDto.from(intent,
                orders.findByIntentId(intent.id()),
                attempts.findLatestByIntentId(intent.id()));
    }
    private com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent requireOwned(
            java.util.UUID id,Authentication authentication){
        return query.findOwned(new ExecutionIntentId(id),principal(authentication).getUserId())
                .orElseThrow(ExecutionNotFoundException::new);
    }
    private UserDto principal(Authentication authentication){return (UserDto)authentication.getPrincipal();}
}
