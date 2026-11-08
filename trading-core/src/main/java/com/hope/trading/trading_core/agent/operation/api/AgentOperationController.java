package com.hope.trading.trading_core.agent.operation.api;

import com.hope.trading.trading_core.agent.operation.application.*;
import com.hope.trading.trading_core.security.AgentPrincipal;
import com.hope.trading.trading_core.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/agent/operation-requests")
public class AgentOperationController {
    private final AgentOperationService service;
    public AgentOperationController(AgentOperationService service) { this.service = service; }
    @PostMapping public ResponseEntity<AgentOperationDtos.Response> request(@Valid @RequestBody AgentOperationDtos.CreateRequest body, Authentication auth, @RequestHeader(value="X-Correlation-ID", required=false) String correlationId) { return ResponseEntity.status(HttpStatus.CREATED).body(service.request(agent(auth), body, correlationId)); }
    @GetMapping("/{id}") public ResponseEntity<AgentOperationDtos.Response> agentStatus(@PathVariable UUID id, Authentication auth, @RequestHeader(value="X-Correlation-ID", required=false) String c) { return ResponseEntity.ok(service.agentGet(agent(auth), id, c)); }
    @PostMapping("/{id}/execute") public ResponseEntity<AgentOperationDtos.Response> execute(@PathVariable UUID id, Authentication auth, @RequestHeader(value="X-Correlation-ID", required=false) String c) { return ResponseEntity.ok(service.execute(agent(auth), id, c)); }
    @GetMapping public ResponseEntity<List<AgentOperationDtos.Response>> list(Authentication auth) { return ResponseEntity.ok(service.listForHuman(user(auth).getUserId())); }
    @GetMapping("/{id}/human") public ResponseEntity<AgentOperationDtos.Response> humanGet(@PathVariable UUID id, Authentication auth) { return ResponseEntity.ok(service.humanGet(user(auth).getUserId(), id)); }
    @PostMapping("/{id}/approve") public ResponseEntity<AgentOperationDtos.Response> approve(@PathVariable UUID id, Authentication auth, @RequestHeader(value="X-Correlation-ID", required=false) String c) { return ResponseEntity.ok(service.approve(user(auth).getUserId(), id, c)); }
    @PostMapping("/{id}/reject") public ResponseEntity<AgentOperationDtos.Response> reject(@PathVariable UUID id, Authentication auth, @RequestHeader(value="X-Correlation-ID", required=false) String c) { return ResponseEntity.ok(service.reject(user(auth).getUserId(), id, c)); }
    private AgentPrincipal agent(Authentication a) { if (a == null || !(a.getPrincipal() instanceof AgentPrincipal p)) throw new AgentOperationException(403, "AGENT_AUTHENTICATION_REQUIRED"); return p; }
    private UserDto user(Authentication a) { if (a == null || !(a.getPrincipal() instanceof UserDto u)) throw new AgentOperationException(403, "HUMAN_AUTHENTICATION_REQUIRED"); return u; }
}
