package com.hope.trading.trading_core.execution.api;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.execution.api.dto.*;
import com.hope.trading.trading_core.execution.application.command.CreateExecutionIntentCommand;
import com.hope.trading.trading_core.execution.application.service.*;
import com.hope.trading.trading_core.execution.domain.model.*;
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
    private final CreateExecutionIntentService creation;
    private final ExecuteTradeService execution;
    private final RetryExecutionService retry;
    private final CancelExecutionService cancellation;
    private final RecoverExecutionService recovery;
    private final QueryExecutionService query;
    public ExecutionController(CreateExecutionIntentService creation,ExecuteTradeService execution,
            RetryExecutionService retry,CancelExecutionService cancellation,
            RecoverExecutionService recovery,QueryExecutionService query){
        this.creation=creation;this.execution=execution;this.retry=retry;
        this.cancellation=cancellation;this.recovery=recovery;this.query=query;
    }
    @PostMapping public ResponseEntity<ExecutionDto> create(
            @Valid @RequestBody CreateExecutionRequest request,Authentication authentication){
        var intent=creation.create(new CreateExecutionIntentCommand(
                new TradePlanReference(request.tradePlanId(),request.tradePlanVersion()),
                new RiskApprovalReference(request.riskEvaluationId(),request.riskDecision(),
                        request.riskApprovedAt()),new IdempotencyKey(request.idempotencyKey()),
                principal(authentication).getUserId(),request.brokerAccountId(),
                new ExecutionParameters(request.instrument(),request.side(),request.orderType(),
                        request.quantity(),request.limitPrice()),request.expiresAt()));
        return ResponseEntity.created(URI.create("/executions/"+intent.id().value()))
                .body(ExecutionDto.from(intent));
    }
    @PostMapping("/{id}/execute") public ResponseEntity<ExecutionDto> execute(
            @PathVariable java.util.UUID id,Authentication authentication){
        requireOwned(id,authentication);return ResponseEntity.ok(
                ExecutionDto.from(execution.execute(new ExecutionIntentId(id))));
    }
    @GetMapping("/{id}") public ResponseEntity<ExecutionDto> get(
            @PathVariable java.util.UUID id,Authentication authentication){
        return ResponseEntity.ok(ExecutionDto.from(requireOwned(id,authentication)));
    }
    @GetMapping public ResponseEntity<List<ExecutionSummaryDto>> list(Authentication authentication){
        return ResponseEntity.ok(query.findOwned(principal(authentication).getUserId())
                .stream().map(ExecutionSummaryDto::from).toList());
    }
    @PostMapping("/{id}/retry") public ResponseEntity<ExecutionDto> retry(
            @PathVariable java.util.UUID id,Authentication authentication){
        requireOwned(id,authentication);return ResponseEntity.ok(
                ExecutionDto.from(retry.retry(new ExecutionIntentId(id))));
    }
    @PostMapping("/{id}/cancel") public ResponseEntity<ExecutionDto> cancel(
            @PathVariable java.util.UUID id,Authentication authentication){
        requireOwned(id,authentication);return ResponseEntity.ok(
                ExecutionDto.from(cancellation.cancel(new ExecutionIntentId(id))));
    }
    @PostMapping("/recovery") public ResponseEntity<List<ExecutionDto>> recover(){
        return ResponseEntity.ok(recovery.recoverAll().stream().map(ExecutionDto::from).toList());
    }
    private com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent requireOwned(
            java.util.UUID id,Authentication authentication){
        return query.findOwned(new ExecutionIntentId(id),principal(authentication).getUserId())
                .orElseThrow(ExecutionNotFoundException::new);
    }
    private UserDto principal(Authentication authentication){return (UserDto)authentication.getPrincipal();}
}
