package com.hope.trading.market_intelligence.config;

import com.hope.trading.market_intelligence.adapter.persistence.*;
import com.hope.trading.market_intelligence.application.execution.*;
import com.hope.trading.market_intelligence.application.planning.*;
import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

import java.time.Clock;
import java.util.List;

@Configuration
public class CapabilityEngineConfiguration {
    @Bean
    CapabilityRegistry capabilityRegistry(List<Capability> capabilities) {
        CapabilityRegistry registry = new CapabilityRegistry();
        capabilities.forEach(registry::register);
        return registry;
    }

    @Bean
    ArtifactAdapterRegistry artifactAdapterRegistry(List<ArtifactAdapter> adapters) {
        ArtifactAdapterRegistry registry = new ArtifactAdapterRegistry();
        adapters.forEach(registry::register);
        return registry;
    }

    @Bean
    ExecutionPlanner executionPlanner(
            CapabilityRegistry capabilities,
            ArtifactAdapterRegistry adapters,
            Clock clock) {
        return new ExecutionPlanner(capabilities, adapters, clock);
    }

    @Bean(destroyMethod = "close")
    LocalCapabilityExecutor localCapabilityExecutor(
            @Value("${intelligence.capabilities.parallelism:4}") int parallelism) {
        return new LocalCapabilityExecutor(parallelism);
    }

    @Bean
    CapabilityExecutionRepository capabilityExecutionRepository() {
        return new InMemoryCapabilityExecutionRepository();
    }

    @Bean
    ArtifactPersistencePort capabilityArtifactPersistencePort() {
        return new InMemoryCapabilityArtifactPersistenceAdapter();
    }

    @Bean
    BackoffCalculator capabilityBackoffCalculator() {
        return new BackoffCalculator();
    }

    @Bean
    RetryDelay capabilityRetryDelay() {
        return new ThreadRetryDelay();
    }

    @Bean
    ExecutionEngine executionEngine(
            LocalCapabilityExecutor executor,
            CapabilityExecutionRepository executions,
            ArtifactPersistencePort artifacts,
            BackoffCalculator backoff,
            RetryDelay delay,
            Clock clock) {
        return new ExecutionEngine(
                executor, executions, artifacts, backoff, delay, clock);
    }
}
