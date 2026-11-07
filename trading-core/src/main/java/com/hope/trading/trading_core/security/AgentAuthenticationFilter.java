package com.hope.trading.trading_core.security;

import com.hope.trading.trading_core.agent.model.AgentCredential;
import com.hope.trading.trading_core.agent.model.AgentIdentity;
import com.hope.trading.trading_core.agent.model.AgentStatus;
import com.hope.trading.trading_core.agent.repository.AgentCredentialRepository;
import com.hope.trading.trading_core.agent.repository.AgentIdentityRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AgentAuthenticationFilter extends OncePerRequestFilter {
    static final String AGENT_AUTHENTICATED = AgentAuthenticationFilter.class.getName() + ".authenticated";
    private final AgentJwtService jwt;
    private final AgentIdentityRepository agents;
    private final AgentCredentialRepository credentials;
    private final Clock clock;

    public AgentAuthenticationFilter(AgentJwtService jwt, AgentIdentityRepository agents,
                                     AgentCredentialRepository credentials, Clock clock) {
        this.jwt = jwt;
        this.agents = agents;
        this.credentials = credentials;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().equals("/api/v1/agents/token")
                || request.getRequestURI().startsWith("/internal/")) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(7).trim();
        try {
            AgentPrincipal principal = jwt.validate(token);
            AgentIdentity agent = agents.findById(principal.agentId()).orElseThrow();
            AgentCredential credential = credentials.findById(principal.credentialId()).orElseThrow();
            Instant now = clock.instant();
            if (!agent.id().equals(credential.agentId())
                    || !agent.ownerUserId().equals(principal.ownerUserId())
                    || agent.status() != AgentStatus.ACTIVE
                    || credential.status() != com.hope.trading.trading_core.agent.model.AgentCredentialStatus.ACTIVE
                    || credential.credentialVersion() != principal.credentialVersion()
                    || !credential.isActiveAt(now)) {
                throw new IllegalArgumentException("Inactive agent token");
            }
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setAttribute(AGENT_AUTHENTICATED, Boolean.TRUE);
        } catch (RuntimeException ignored) {
            // The human filter will reject invalid bearer material; valid human tokens pass through.
        }
        chain.doFilter(request, response);
    }
}
