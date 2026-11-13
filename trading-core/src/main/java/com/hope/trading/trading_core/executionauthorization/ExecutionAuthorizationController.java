package com.hope.trading.trading_core.executionauthorization;

import com.hope.trading.trading_core.agent.operation.application.AgentOperationException;
import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.execution.api.dto.ExecutionDto;
import com.hope.trading.trading_core.security.AgentPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class ExecutionAuthorizationController {
    private final ExecutionAuthorizationService service;
    public ExecutionAuthorizationController(ExecutionAuthorizationService service) { this.service = service; }

    @PostMapping("/api/v1/agent/execution-authorizations")
    public ResponseEntity<ExecutionAuthorizationDtos.Response> request(
            @RequestBody ExecutionAuthorizationDtos.Request request, Authentication authentication,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ResponseEntity.ok(service.request(agent(authentication), request, correlationId));
    }
    @GetMapping("/api/v1/agent/execution-authorizations/{id}")
    public ResponseEntity<ExecutionAuthorizationDtos.Response> agentGet(@PathVariable UUID id,
            Authentication authentication, @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ResponseEntity.ok(service.agentGet(agent(authentication), id, correlationId));
    }
    @PostMapping("/api/v1/agent/execution-authorizations/{id}/continue")
    public ResponseEntity<ExecutionDto> continueApproved(@PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String key, Authentication authentication,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        var intent = service.continueApproved(agent(authentication), id, key, correlationId);
        return ResponseEntity.ok(ExecutionDto.from(intent));
    }
    @GetMapping("/api/v1/agent/executions/{id}")
    public ResponseEntity<ExecutionDto> agentExecutionGet(@PathVariable UUID id,
            @RequestParam UUID brokerAccountId, Authentication authentication,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ResponseEntity.ok(ExecutionDto.from(service.agentExecutionGet(agent(authentication), id,
                brokerAccountId, correlationId)));
    }
    @GetMapping("/api/v1/execution-authorizations/{id}")
    public ResponseEntity<ExecutionAuthorizationDtos.Response> humanGet(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(service.humanGet(user(authentication).getUserId(), id));
    }
    @PostMapping("/api/v1/execution-authorizations/{id}/approve")
    public ResponseEntity<ExecutionAuthorizationDtos.Response> approve(@PathVariable UUID id,
            @RequestBody ExecutionAuthorizationDtos.StepUpRequest request, Authentication authentication,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ResponseEntity.ok(service.decide(user(authentication).getUserId(), id, true,
                request == null ? null : request.password(), correlationId));
    }
    @PostMapping("/api/v1/execution-authorizations/{id}/reject")
    public ResponseEntity<ExecutionAuthorizationDtos.Response> reject(@PathVariable UUID id,
            @RequestBody ExecutionAuthorizationDtos.StepUpRequest request, Authentication authentication,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ResponseEntity.ok(service.decide(user(authentication).getUserId(), id, false,
                request == null ? null : request.password(), correlationId));
    }
    @PostMapping("/api/v1/execution-authorizations/{id}/revoke")
    public ResponseEntity<ExecutionAuthorizationDtos.Response> revoke(@PathVariable UUID id,
            @RequestBody ExecutionAuthorizationDtos.StepUpRequest request, Authentication authentication,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ResponseEntity.ok(service.revoke(user(authentication).getUserId(), id,
                request == null ? null : request.password(), correlationId));
    }
    private AgentPrincipal agent(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AgentPrincipal value))
            throw new AgentOperationException(403, "AGENT_AUTHENTICATION_REQUIRED");
        return value;
    }
    private UserDto user(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDto value))
            throw new AgentOperationException(403, "HUMAN_AUTHENTICATION_REQUIRED");
        return value;
    }
}
