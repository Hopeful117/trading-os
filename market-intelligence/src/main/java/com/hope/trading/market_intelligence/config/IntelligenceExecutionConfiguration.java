package com.hope.trading.market_intelligence.config;

import com.hope.trading.market_intelligence.domain.artifact.FreshnessEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

    @Bean(name = "researchExecutor", destroyMethod = "shutdown")
    ExecutorService researchExecutor(
            @Value("${research.execution.max-concurrency:4}") int maxConcurrency,
            @Value("${research.execution.queue-capacity:32}") int queueCapacity) {
        if (maxConcurrency < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException("Research execution limits must be positive");
        }
        return new ThreadPoolExecutor(
                maxConcurrency, maxConcurrency, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
    }
}
