package com.hope.trading.broker_service.kraken.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix="kraken")
public class KrakenProperties {
    private String baseUrl;
    private String apiKey;
    private String apiSecret;
    private Duration connectTimeout;
    private Duration readTimeout;
    private int readMaxAttempts = 3;
    private int requestsPerSecond = 15;
    private long capabilityVersion = 1;
    private String marginCurrency = "USD";
    private List<BigDecimal> supportedLeverageLevels = new ArrayList<>(List.of(BigDecimal.ONE));
}
