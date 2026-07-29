package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.observation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ObservationRepository {
    Observation save(Observation observation);
    void supersede(Observation previous, Observation replacement);
    Optional<Observation> findById(UUID id);
    List<Observation> findByInstrument(String instrument);
    List<Observation> findActive();
    List<Observation> findByType(ObservationType type);
    List<Observation> findByStatus(ObservationStatus status);
    List<Observation> findByHorizon(String horizon);
    List<Observation> findByCategory(String category);
    List<Observation> findByConfidence(BigDecimal minimum, BigDecimal maximum);
    List<Observation> findByTimeRange(Instant fromInclusive, Instant toExclusive);
}
