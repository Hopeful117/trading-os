package com.hope.trading.market_intelligence.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class MiAgentJwtAuthenticationFilter extends OncePerRequestFilter {
    private final MiAgentJwtProperties properties;

    public MiAgentJwtAuthenticationFilter(MiAgentJwtProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey()).requireIssuer(properties.getIssuer())
                    .requireAudience(properties.getAudience()).require("type", "agent")
                    .build().parseSignedClaims(header.substring(7).trim()).getPayload();
            UUID agentId = UUID.fromString(claims.get("agent_id", String.class));
            UUID ownerId = UUID.fromString(claims.get("owner_id", String.class));
            UUID credentialId = UUID.fromString(claims.get("credential_id", String.class));
            Integer version = claims.get("credential_version", Integer.class);
            if (!agentId.equals(UUID.fromString(claims.getSubject())) || version == null || version < 1
                    || claims.getId() == null || claims.getIssuedAt() == null || claims.getExpiration() == null) {
                throw new IllegalArgumentException("Invalid agent token");
            }
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new MiAgentPrincipal(agentId, ownerId, credentialId, version, claims.getId()), null, List.of()));
        } catch (Exception invalidToken) {
            // Human JWT authentication is evaluated by the next filter.
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret()));
    }
}
