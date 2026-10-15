package com.hope.trading.news.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfiguration {
    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
