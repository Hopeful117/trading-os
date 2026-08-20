package com.hope.trading.market_intelligence.adapter.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignAuthorizationConfiguration {
    @Bean
    RequestInterceptor authenticatedRequestInterceptor() {
        return template -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
                if (authorization != null && !authorization.isBlank()) {
                    template.header(HttpHeaders.AUTHORIZATION, authorization);
                }
                String correlationId = attributes.getRequest().getHeader("X-Correlation-ID");
                if (correlationId != null && !correlationId.isBlank()) {
                    template.header("X-Correlation-ID", correlationId);
                }
            }
        };
    }
}
