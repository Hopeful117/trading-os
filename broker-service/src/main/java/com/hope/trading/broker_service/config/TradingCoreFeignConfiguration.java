package com.hope.trading.broker_service.config;

import com.hope.trading.broker_service.security.BrokerPrincipal;
import com.hope.trading.broker_service.security.ServiceJwtService;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public class TradingCoreFeignConfiguration {
    private static final String TRADING_CORE_AUDIENCE = "trading-core";

    private final ServiceJwtService serviceJwtService;

    public TradingCoreFeignConfiguration(ServiceJwtService serviceJwtService) {
        this.serviceJwtService = serviceJwtService;
    }

    @Bean
    public RequestInterceptor tradingCoreAuthenticationInterceptor() {
        return template -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            UUID actorId = authentication != null && authentication.getPrincipal() instanceof BrokerPrincipal principal
                    ? principal.userId()
                    : null;
            template.header("X-Service-Authorization",
                    "Bearer " + serviceJwtService.issue(TRADING_CORE_AUDIENCE, actorId));
        };
    }
}
