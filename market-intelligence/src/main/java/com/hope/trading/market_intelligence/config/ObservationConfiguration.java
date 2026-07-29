package com.hope.trading.market_intelligence.config;

import com.hope.trading.market_intelligence.adapter.persistence.InMemoryObservationRepository;
import com.hope.trading.market_intelligence.application.observation.*;
import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.observation.ObservationFactory;
import org.springframework.context.annotation.*;

import java.time.Clock;

@Configuration
public class ObservationConfiguration {
    @Bean ObservationRepository observationRepository() {
        return new InMemoryObservationRepository();
    }

    @Bean ObservationFactory observationFactory() {
        return new ObservationFactory();
    }

    @Bean ObservationBuilder observationBuilder(
            CapabilityExecutionRepository executions,
            ObservationRepository observations,
            ObservationFactory factory,
            Clock clock
    ) {
        return new ObservationBuilder(executions, observations, factory, clock);
    }

    @Bean ObservationQueryService observationQueryService(ObservationRepository repository) {
        return new ObservationQueryService(repository);
    }
}
