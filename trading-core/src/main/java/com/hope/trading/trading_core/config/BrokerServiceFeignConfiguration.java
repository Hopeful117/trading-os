package com.hope.trading.trading_core.config;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.security.AgentPrincipal;
import com.hope.trading.trading_core.security.ServiceJwtService;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public class BrokerServiceFeignConfiguration {
    static final String BROKER_AUDIENCE = "broker-service";

    private final ServiceJwtService serviceJwtService;

    public BrokerServiceFeignConfiguration(ServiceJwtService serviceJwtService) {
        this.serviceJwtService = serviceJwtService;
    }

    @Bean
    public RequestInterceptor brokerServiceAuthenticationInterceptor() {
        return template -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            UUID actorId = null;
            if (authentication != null && authentication.getPrincipal() instanceof UserDto user) {
                actorId = user.getUserId();
            } else if (authentication != null && authentication.getPrincipal() instanceof AgentPrincipal agent) {
                actorId = agent.ownerUserId();
            }
            if (actorId == null) {
                actorId = internalDelegatedActor(template);
            }
            AgentPrincipal delegatedAgent = authentication != null
                    && authentication.getPrincipal() instanceof AgentPrincipal agent ? agent : null;
            template.header("X-Service-Authorization",
                    "Bearer " + serviceJwtService.issue(BROKER_AUDIENCE, actorId, delegatedAgent));
        };
    }

    private UUID internalDelegatedActor(feign.RequestTemplate template) {
        var values = template.headers().get("X-Internal-Delegated-Actor");
        if (values == null || values.isEmpty()) return null;
        String value = values.iterator().next();
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
