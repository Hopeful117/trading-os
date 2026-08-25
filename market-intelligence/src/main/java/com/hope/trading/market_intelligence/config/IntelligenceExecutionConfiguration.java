package com.hope.trading.market_intelligence.config;

import com.hope.trading.market_intelligence.domain.artifact.FreshnessEvaluator;
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

    @Bean
    FreshnessEvaluator artifactFreshnessEvaluator() {
        return new FreshnessEvaluator();
    }

    @Bean(name = "intelligenceCapabilityExecutor", destroyMethod = "shutdown")
    ExecutorService intelligenceCapabilityExecutor() {
        return Executors.newFixedThreadPool(4);
    }

    @Bean(name = "analysisExecutionDispatcherExecutor", destroyMethod = "shutdown")
    ExecutorService analysisExecutionDispatcherExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Owns scan-level dispatch work (claim + enqueue of every eligible market)
     * so that it never runs on the HTTP request thread. Analyses themselves are
     * still handed over to {@code analysisExecutionDispatcherExecutor}.
     */
    @Bean(name = "scanDispatchExecutor", destroyMethod = "shutdown")
    ExecutorService scanDispatchExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
