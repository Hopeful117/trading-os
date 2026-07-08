package com.hope.trading.broker_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix="kraken")
public class KrakenProperties {
    private String baseUrl;
    private String apiKey;
    private String apiSecret;
    private Duration connectTimeout;
    private Duration readTimeout;
}
