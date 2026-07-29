package com.hope.trading.market_intelligence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class IntelligenceExecutionConfiguration {
    @Bean
    Clock intelligenceClock() {
        return Clock.systemUTC();
    }

    @Bean(name = "intelligenceCapabilityExecutor", destroyMethod = "shutdown")
    ExecutorService intelligenceCapabilityExecutor() {
        return Executors.newFixedThreadPool(4);
    }

    @Bean(name = "analysisExecutionDispatcherExecutor", destroyMethod = "shutdown")
    ExecutorService analysisExecutionDispatcherExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
