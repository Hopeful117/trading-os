package com.hope.trading.gateway.security;

import com.hope.trading.gateway.dto.UserAuthenticationDto;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthenticatedActorHeaderFilter implements GlobalFilter, Ordered {
    static final String ACTOR_HEADER = "X-Actor-Id";
    private static final String INTELLIGENCE_PATH_PREFIX = "/api/v1/intelligence/";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        boolean intelligenceRoute = exchange.getRequest().getPath().value().startsWith(INTELLIGENCE_PATH_PREFIX);
        return ReactiveSecurityContextHolder.getContext()
                .map(org.springframework.security.core.context.SecurityContext::getAuthentication)
                .filter(Authentication.class::isInstance)
                .map(Authentication::getPrincipal)
                .filter(UserAuthenticationDto.class::isInstance)
                .cast(UserAuthenticationDto.class)
                 .filter(principal -> intelligenceRoute)
                 .map(principal -> exchange.mutate().request(request -> request.headers(headers -> {
                     headers.remove(ACTOR_HEADER);
                     headers.set(ACTOR_HEADER, principal.getUserId().toString());
                 })).build())
                 .switchIfEmpty(Mono.fromSupplier(() -> exchange.mutate().request(request ->
                         request.headers(headers -> headers.remove(ACTOR_HEADER))).build()))
                 .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
