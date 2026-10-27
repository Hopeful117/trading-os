package com.hope.trading.trading_core.config;

import com.hope.trading.trading_core.dto.UserDto;
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
            UUID actorId = delegatedActor(template);
            if (actorId == null && authentication != null && authentication.getPrincipal() instanceof UserDto user) {
                actorId = user.getUserId();
            }
            template.header("X-Service-Authorization",
                    "Bearer " + serviceJwtService.issue(BROKER_AUDIENCE, actorId));
        };
    }

    private UUID delegatedActor(feign.RequestTemplate template) {
        var values = template.headers().get("X-Delegated-Actor");
        if (values == null || values.isEmpty()) return null;
        String value = values.iterator().next();
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
