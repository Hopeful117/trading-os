package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.application.ResearchScopeService;
import com.hope.trading.trading_core.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/research-scopes")
public class ResearchScopeController {
    private final ResearchScopeService scopes;

    public ResearchScopeController(ResearchScopeService scopes) {
        this.scopes = scopes;
    }

    @PostMapping
    public ResponseEntity<ResearchScopeResponse> create(@Valid @RequestBody ResearchScopeRequest request,
                                                         Authentication authentication) {
        UUID owner = userId(authentication);
        ResearchScopeResponse response = ResearchScopeResponse.from(scopes.create(owner, request));
        return ResponseEntity.created(URI.create("/api/v1/research-scopes/" + response.id())).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ResearchScopeResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(scopes.list(userId(authentication)).stream().map(ResearchScopeResponse::from).toList());
    }

    @PostMapping("/{scopeId}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable UUID scopeId, Authentication authentication) {
        scopes.revoke(userId(authentication), scopeId);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDto user)
                || user.getUserId() == null) {
            throw new AgentAuthenticationException();
        }
        return user.getUserId();
    }
}
