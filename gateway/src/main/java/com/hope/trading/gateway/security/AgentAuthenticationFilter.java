package com.hope.trading.gateway.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class AgentAuthenticationFilter implements WebFilter {
    static final String AGENT_AUTHENTICATED = AgentAuthenticationFilter.class.getName() + ".authenticated";
    private final AgentJwtService jwt;

    public AgentAuthenticationFilter(AgentJwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if ("/api/v1/agents/token".equals(path)) {
            return chain.filter(exchange);
        }
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }
        try {
            AgentPrincipal principal = jwt.validate(header.substring(7).trim());
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
            SecurityContext context = new SecurityContextImpl(authentication);
            exchange.getAttributes().put(AGENT_AUTHENTICATED, Boolean.TRUE);
            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
        } catch (RuntimeException ignored) {
            return chain.filter(exchange);
        }
    }
}
