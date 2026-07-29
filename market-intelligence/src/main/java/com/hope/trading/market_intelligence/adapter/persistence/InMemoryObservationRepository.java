package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.ObservationRepository;
import com.hope.trading.market_intelligence.domain.observation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryObservationRepository implements ObservationRepository {
    private final ConcurrentMap<UUID, Observation> store = new ConcurrentHashMap<>();

    @Override
    public Observation save(Observation observation) {
        Objects.requireNonNull(observation);
        store.compute(observation.id(), (id, existing) -> {
            if (existing != null
                    && existing.status() == ObservationStatus.SUPERSEDED
                    && observation.status() != ObservationStatus.SUPERSEDED) {
                throw new IllegalStateException("A superseded observation cannot be replaced");
            }
            return observation;
        });
        return observation;
    }

    @Override
    public synchronized void supersede(Observation previous, Observation replacement) {
        if (previous.status() != ObservationStatus.SUPERSEDED
                || replacement.status() != ObservationStatus.ACTIVE
                || !previous.supersededBy().filter(replacement.id()::equals).isPresent()
                || !replacement.supersedes().filter(previous.id()::equals).isPresent()
                || !previous.lineageId().equals(replacement.lineageId())
                || replacement.version() != previous.version() + 1) {
            throw new IllegalArgumentException("Invalid observation version relationship");
        }
        Observation persisted = store.get(previous.id());
        if (persisted == null || persisted.status() != ObservationStatus.ACTIVE) {
            throw new IllegalStateException("Previous active observation is missing");
        }
        store.put(previous.id(), previous);
        store.put(replacement.id(), replacement);
    }

    @Override public Optional<Observation> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }
    @Override public List<Observation> findByInstrument(String instrument) {
        return matching(item -> item.instrument().equalsIgnoreCase(instrument));
    }
    @Override public List<Observation> findActive() {
        return matching(item -> item.status() == ObservationStatus.ACTIVE);
    }
    @Override public List<Observation> findByType(ObservationType type) {
        return matching(item -> item.type().equals(type));
    }
    @Override public List<Observation> findByStatus(ObservationStatus status) {
        return matching(item -> item.status() == status);
    }
    @Override public List<Observation> findByHorizon(String horizon) {
        return matching(item -> item.horizon().equalsIgnoreCase(horizon));
    }
    @Override public List<Observation> findByCategory(String category) {
        return matching(item -> item.categories().stream()
                .anyMatch(value -> value.equalsIgnoreCase(category)));
    }
    @Override public List<Observation> findByConfidence(BigDecimal minimum, BigDecimal maximum) {
        if (minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("minimum cannot exceed maximum");
        }
        return matching(item -> item.confidence().score().compareTo(minimum) >= 0
                && item.confidence().score().compareTo(maximum) <= 0);
    }
    @Override public List<Observation> findByTimeRange(
            Instant fromInclusive, Instant toExclusive) {
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("Invalid time range");
        }
        return matching(item -> !item.createdAt().isBefore(fromInclusive)
                && item.createdAt().isBefore(toExclusive));
    }

    private List<Observation> matching(java.util.function.Predicate<Observation> predicate) {
        return store.values().stream().filter(predicate)
                .sorted(Comparator.comparing(Observation::createdAt)
                        .thenComparing(Observation::id))
                .toList();
    }
}
