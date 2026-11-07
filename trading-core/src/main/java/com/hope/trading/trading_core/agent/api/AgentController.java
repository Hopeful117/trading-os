package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.application.AgentLifecycleService;
import com.hope.trading.trading_core.agent.application.AgentGrantService;
import com.hope.trading.trading_core.agent.application.AgentTokenExchangeService;
import com.hope.trading.trading_core.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {
    private final AgentLifecycleService service;
    private final AgentGrantService grants;
    private final AgentTokenExchangeService tokenExchange;

    public AgentController(AgentLifecycleService service, AgentGrantService grants) {
        this(service, grants, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AgentController(AgentLifecycleService service, AgentGrantService grants,
                           AgentTokenExchangeService tokenExchange) {
        this.service = service;
        this.grants = grants;
        this.tokenExchange = tokenExchange;
    }

    @PostMapping("/token")
    public ResponseEntity<AgentTokenResponse> exchangeToken(
            @Valid @RequestBody AgentTokenRequest request,
            HttpServletRequest httpRequest,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        return ResponseEntity.ok(tokenExchange.exchange(request, httpRequest.getRemoteAddr(), correlationId));
    }

    @PostMapping
    public ResponseEntity<AgentResponse> register(@Valid @RequestBody AgentRequest request,
                                                  Authentication authentication,
                                                  @RequestHeader(value = "X-Correlation-ID", required = false)
                                                  String correlationId) {
        AgentResponse response = service.register(principal(authentication).getUserId(), request, correlationId);
        return ResponseEntity.created(URI.create("/api/v1/agents/" + response.id())).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AgentResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(service.list(principal(authentication).getUserId()));
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<AgentResponse> get(@PathVariable UUID agentId, Authentication authentication) {
        return ResponseEntity.ok(service.get(principal(authentication).getUserId(), agentId));
    }

    @PostMapping("/{agentId}/suspend")
    public ResponseEntity<Void> suspend(@PathVariable UUID agentId, Authentication authentication,
                                        @RequestHeader(value = "X-Correlation-ID", required = false)
                                        String correlationId) {
        service.suspend(principal(authentication).getUserId(), agentId, correlationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{agentId}/activate")
    public ResponseEntity<Void> activate(@PathVariable UUID agentId, Authentication authentication,
                                         @RequestHeader(value = "X-Correlation-ID", required = false)
                                         String correlationId) {
        service.activate(principal(authentication).getUserId(), agentId, correlationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{agentId}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable UUID agentId, Authentication authentication,
                                       @RequestHeader(value = "X-Correlation-ID", required = false)
                                       String correlationId) {
        service.revoke(principal(authentication).getUserId(), agentId, correlationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{agentId}/credentials")
    public ResponseEntity<AgentCredentialResponse> issueCredential(
            @PathVariable UUID agentId, Authentication authentication,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        return ResponseEntity.ok(service.issueCredential(principal(authentication).getUserId(), agentId, correlationId));
    }

    @PostMapping("/{agentId}/credentials/{credentialId}/revoke")
    public ResponseEntity<Void> revokeCredential(@PathVariable UUID agentId, @PathVariable UUID credentialId,
                                                 Authentication authentication,
                                                 @RequestHeader(value = "X-Correlation-ID", required = false)
                                                 String correlationId) {
        service.revokeCredential(principal(authentication).getUserId(), agentId, credentialId, correlationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{agentId}/grants")
    public ResponseEntity<AgentGrantResponse> createGrant(
            @PathVariable UUID agentId, @Valid @RequestBody AgentGrantRequest request,
            Authentication authentication,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        return ResponseEntity.status(201).body(grants.create(principal(authentication).getUserId(), agentId,
                request, correlationId));
    }

    @GetMapping("/{agentId}/grants")
    public ResponseEntity<List<AgentGrantResponse>> listGrants(@PathVariable UUID agentId,
                                                                Authentication authentication) {
        return ResponseEntity.ok(grants.list(principal(authentication).getUserId(), agentId));
    }

    @GetMapping("/{agentId}/grants/{grantId}")
    public ResponseEntity<AgentGrantResponse> getGrant(@PathVariable UUID agentId, @PathVariable UUID grantId,
                                                        Authentication authentication) {
        return ResponseEntity.ok(grants.get(principal(authentication).getUserId(), agentId, grantId));
    }

    @PostMapping("/{agentId}/grants/{grantId}/revoke")
    public ResponseEntity<Void> revokeGrant(@PathVariable UUID agentId, @PathVariable UUID grantId,
                                             Authentication authentication,
                                             @RequestHeader(value = "X-Correlation-ID", required = false)
                                             String correlationId) {
        grants.revoke(principal(authentication).getUserId(), agentId, grantId, correlationId);
        return ResponseEntity.noContent().build();
    }

    private UserDto principal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserDto user)
                || user.getUserId() == null) {
            throw new AgentAuthenticationException();
        }
        return user;
    }
}
