package com.hope.trading.market_intelligence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class MarketIntelligenceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketIntelligenceApplication.class, args);
    }
}
