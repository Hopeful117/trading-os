package com.hope.trading.trading_core.config;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.security.AgentPrincipal;
import com.hope.trading.trading_core.security.ServiceJwtService;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public class MarketIntelligenceFeignConfiguration {
    static final String MARKET_INTELLIGENCE_AUDIENCE = "market-intelligence";

    private final ServiceJwtService serviceJwtService;

    public MarketIntelligenceFeignConfiguration(ServiceJwtService serviceJwtService) {
        this.serviceJwtService = serviceJwtService;
    }

    @Bean
    public RequestInterceptor marketIntelligenceAuthenticationInterceptor() {
        return template -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            UUID actorId = null;
            if (authentication != null && authentication.getPrincipal() instanceof UserDto user) {
                actorId = user.getUserId();
            } else if (authentication != null && authentication.getPrincipal() instanceof AgentPrincipal agent) {
                actorId = agent.ownerUserId();
            }
            AgentPrincipal delegatedAgent = authentication != null
                    && authentication.getPrincipal() instanceof AgentPrincipal agent ? agent : null;
            template.header("X-Service-Authorization", "Bearer " +
                    serviceJwtService.issue(MARKET_INTELLIGENCE_AUDIENCE, actorId, delegatedAgent));
        };
    }
}
