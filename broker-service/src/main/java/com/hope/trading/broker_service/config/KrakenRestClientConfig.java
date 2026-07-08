package com.hope.trading.broker_service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class KrakenRestClientConfig {
    private final KrakenProperties properties;

    @Bean
    public RestClient krakenRestClient() {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl()).build();

    }
}
