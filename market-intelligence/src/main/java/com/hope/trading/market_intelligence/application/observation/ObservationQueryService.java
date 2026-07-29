package com.hope.trading.market_intelligence.application.observation;

import com.hope.trading.market_intelligence.application.port.ObservationRepository;
import com.hope.trading.market_intelligence.domain.observation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class ObservationQueryService {
    private final ObservationRepository repository;

    public ObservationQueryService(ObservationRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public List<Observation> findByInstrument(String instrument) {
        return repository.findByInstrument(instrument);
    }
    public List<Observation> findActive() { return repository.findActive(); }
    public List<Observation> findByType(ObservationType type) {
        return repository.findByType(type);
    }
    public List<Observation> findByStatus(ObservationStatus status) {
        return repository.findByStatus(status);
    }
    public List<Observation> findByHorizon(String horizon) {
        return repository.findByHorizon(horizon);
    }
    public List<Observation> findByCategory(String category) {
        return repository.findByCategory(category);
    }
    public List<Observation> findByConfidence(BigDecimal minimum, BigDecimal maximum) {
        return repository.findByConfidence(minimum, maximum);
    }
    public List<Observation> findByTimeRange(Instant fromInclusive, Instant toExclusive) {
        return repository.findByTimeRange(fromInclusive, toExclusive);
    }
}
