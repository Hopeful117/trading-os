package com.hope.trading.trading_core.config;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.security.ServiceJwtService;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

public class MarketDataFeignConfiguration {
    private static final String AUDIENCE = "market-data";
    private final ServiceJwtService serviceJwtService;

    public MarketDataFeignConfiguration(ServiceJwtService serviceJwtService) {
        this.serviceJwtService = serviceJwtService;
    }

    @Bean
    RequestInterceptor marketDataAuthorizationInterceptor() {
        return template -> {
            if (template.url().startsWith("/internal/")) {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                UUID actorId = authentication != null && authentication.getPrincipal() instanceof UserDto user
                        ? user.getUserId() : null;
                template.header("X-Service-Authorization",
                        "Bearer " + serviceJwtService.issue(AUDIENCE, actorId));
                return;
            }
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
                if (authorization != null && !authorization.isBlank()) {
                    template.header(HttpHeaders.AUTHORIZATION, authorization);
                }
            }
        };
    }
}
