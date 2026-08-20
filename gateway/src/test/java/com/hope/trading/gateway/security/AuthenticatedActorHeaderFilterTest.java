package com.hope.trading.gateway.security;

import com.hope.trading.gateway.dto.UserAuthenticationDto;
import com.hope.trading.gateway.helper.Role;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedActorHeaderFilterTest {
    @Test
    void injectsActorHeaderForIntelligenceRoutes() {
        UUID actorId = UUID.randomUUID();
        UserAuthenticationDto principal = UserAuthenticationDto.builder()
                .userId(actorId)
                .username("trader")
                .email("trader@test.local")
                .role(Role.ROLE_USER)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/intelligence/scans").build()
        );
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = value -> {
            forwarded.set(value);
            value.getResponse().setStatusCode(HttpStatus.OK);
            return value.getResponse().setComplete();
        };

        new AuthenticatedActorHeaderFilter().filter(exchange, chain)
                .contextWrite(org.springframework.security.core.context.ReactiveSecurityContextHolder.withAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null)
                ))
                .block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(AuthenticatedActorHeaderFilter.ACTOR_HEADER))
                .isEqualTo(actorId.toString());
    }

    @Test
    void leavesNonIntelligenceRoutesUnchanged() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/accounts").build()
        );
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = value -> {
            forwarded.set(value.getRequest());
            value.getResponse().setStatusCode(HttpStatus.OK);
            return value.getResponse().setComplete();
        };

        new AuthenticatedActorHeaderFilter().filter(exchange, chain).block();

        assertThat(forwarded.get().getHeaders().getFirst(AuthenticatedActorHeaderFilter.ACTOR_HEADER))
                .isNull();
    }
}
