package com.hope.trading.market_intelligence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class IntelligenceExecutionConfiguration {
    @Bean(destroyMethod = "shutdown")
    ExecutorService intelligenceExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
