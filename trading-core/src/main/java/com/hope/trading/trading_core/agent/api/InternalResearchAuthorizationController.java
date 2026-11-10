package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.application.AgentAuthorizationService;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import com.hope.trading.trading_core.agent.model.AgentPermission;
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
        AgentPermission permission;
        try {
            permission = request.permission() == null
                    ? AgentPermission.RESEARCH_RUN : AgentPermission.fromValue(request.permission());
        } catch (IllegalArgumentException invalidPermission) {
            return ResponseEntity.badRequest().build();
        }
        AgentAuthorizationService.Decision decision = permission == AgentPermission.RESEARCH_RUN
                ? authorization.authorizeResearch(request.agentId(), request.credentialId(), request.credentialVersion(),
                request.scopeId(), request.marketIds(), request.correlationId())
                : permission == AgentPermission.RESEARCH_INSPECT && request.brokerAccountId() != null
                ? authorization.authorizeHistoricalResearchForBrokerAccount(request.agentId(), request.credentialId(),
                request.credentialVersion(), request.brokerAccountId(), request.marketIds(), request.startCutoff(),
                request.endCutoff(), request.datasetProfile(), request.observations(), request.correlationId())
                : authorization.authorizeHistoricalResearch(request.agentId(), request.credentialId(),
                request.credentialVersion(), permission, request.scopeId(), request.marketIds(),
                request.startCutoff(), request.endCutoff(), request.datasetProfile(), request.observations(),
                request.exportRows(), request.exportBytes(), request.durationSeconds(), request.correlationId());
        if (!decision.allowed()) return ResponseEntity.status(403)
                .body(new AuthorizationResponse(false, decision.denialReason(), null, null, null,
                        null, null, null, null));
        return ResponseEntity.ok(new AuthorizationResponse(true, null, decision.grantId(),
                decision.grantVersion(), decision.agentVersion(), decision.ownerUserId(),
                decision.credentialId(), decision.credentialVersion(), request.agentId()));
    }

    public record AuthorizationRequest(UUID agentId, UUID credentialId, Integer credentialVersion,
                                       UUID scopeId, UUID brokerAccountId, Set<UUID> marketIds, String correlationId,
                                       String permission, java.time.Instant startCutoff,
                                       java.time.Instant endCutoff, String datasetProfile,
                                       long observations, long exportRows, long exportBytes,
                                       long durationSeconds) {
        public AuthorizationRequest(UUID agentId, UUID credentialId, Integer credentialVersion,
                                     UUID scopeId, Set<UUID> marketIds, String correlationId) {
            this(agentId, credentialId, credentialVersion, scopeId, null, marketIds, correlationId,
                    AgentPermission.RESEARCH_RUN.value(), null, null, null, 0, 0, 0, 0);
        }
    }
    public record AuthorizationResponse(boolean allowed, String denialReason, UUID grantId,
                                        Long grantVersion, Long agentVersion, UUID ownerUserId,
                                        UUID credentialId, Integer credentialVersion, UUID agentId) { }
}
