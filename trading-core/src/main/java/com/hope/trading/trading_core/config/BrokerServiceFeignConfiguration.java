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
            UUID actorId = authentication != null && authentication.getPrincipal() instanceof UserDto user
                    ? user.getUserId()
                    : null;
            template.header("X-Service-Authorization",
                    "Bearer " + serviceJwtService.issue(BROKER_AUDIENCE, actorId));
        };
    }
}
