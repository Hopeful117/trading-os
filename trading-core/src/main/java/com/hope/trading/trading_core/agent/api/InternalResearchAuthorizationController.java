package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.application.AgentAuthorizationService;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import com.hope.trading.trading_core.security.ServicePrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/agent/research")
public class InternalResearchAuthorizationController {
    private final AgentAuthorizationService authorization;

    public InternalResearchAuthorizationController(AgentAuthorizationService authorization) {
        this.authorization = authorization;
    }

    @PostMapping("/authorize")
    public ResponseEntity<AuthorizationResponse> authorize(
            @Valid @RequestBody AuthorizationRequest request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ServicePrincipal)) {
            return ResponseEntity.status(401).build();
        }
        AgentAuthorizationService.Decision decision = authorization.authorizeResearch(
                request.agentId(), request.credentialId(), request.credentialVersion(),
                request.scopeId(), request.marketIds(), request.correlationId());
        if (!decision.allowed()) return ResponseEntity.status(403)
                .body(new AuthorizationResponse(false, decision.denialReason(), null, null, null,
                        null, null, null, null));
        return ResponseEntity.ok(new AuthorizationResponse(true, null, decision.grantId(),
                decision.grantVersion(), decision.agentVersion(), decision.ownerUserId(),
                decision.credentialId(), decision.credentialVersion(), request.agentId()));
    }

    public record AuthorizationRequest(UUID agentId, UUID credentialId, Integer credentialVersion,
                                       UUID scopeId, Set<UUID> marketIds, String correlationId) { }
    public record AuthorizationResponse(boolean allowed, String denialReason, UUID grantId,
                                        Long grantVersion, Long agentVersion, UUID ownerUserId,
                                        UUID credentialId, Integer credentialVersion, UUID agentId) { }
}
