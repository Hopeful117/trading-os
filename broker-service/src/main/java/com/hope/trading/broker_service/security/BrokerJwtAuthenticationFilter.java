package com.hope.trading.broker_service.security;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class BrokerJwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtSecurityProperties properties;

    public BrokerJwtAuthenticationFilter(JwtSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
                Claims claims = Jwts.parser().verifyWith(key).requireIssuer(properties.issuer()).build()
                        .parseSignedClaims(header.substring(7)).getPayload();
                BrokerPrincipal principal = new BrokerPrincipal(
                        UUID.fromString(claims.getSubject()),
                        claims.get("username", String.class),
                        claims.get("role", String.class));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null,
                                List.of(new SimpleGrantedAuthority(principal.role()))));
            } catch (RuntimeException exception) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
