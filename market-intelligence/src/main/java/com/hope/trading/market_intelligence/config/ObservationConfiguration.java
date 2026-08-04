package com.hope.trading.market_intelligence.config;

import com.hope.trading.market_intelligence.adapter.persistence.InMemoryObservationRepository;
import com.hope.trading.market_intelligence.application.observation.*;
import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.observation.ObservationFactory;
import org.springframework.context.annotation.*;

import java.time.Clock;

@Configuration
public class ObservationConfiguration {

    @Bean ObservationFactory observationFactory() {
        return new ObservationFactory();
    }

    @Bean ObservationRehydrator observationRehydrator(ObservationFactory factory) {
        return snapshot -> factory.restore(
                snapshot.id(), snapshot.lineageId(), snapshot.version(), snapshot.instrument(),
                snapshot.type(), snapshot.status(), snapshot.title(), snapshot.explanation(),
                snapshot.categories(), snapshot.horizon(), snapshot.createdAt(),
                snapshot.validFrom(), snapshot.validUntil(), snapshot.supersedes(),
                snapshot.supersededBy(), snapshot.ruleVersion(), snapshot.evidence());
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
