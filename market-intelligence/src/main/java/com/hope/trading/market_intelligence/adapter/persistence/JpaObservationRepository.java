package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.port.ObservationRepository;
import com.hope.trading.market_intelligence.application.port.ObservationRehydrator;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.observation.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

@Repository
public class JpaObservationRepository implements ObservationRepository {
    private final SpringDataObservationRepository repository;
    private final SpringDataObservationEvidenceRepository evidenceRepository;
    private final ObjectMapper mapper;
    private final ObservationRehydrator rehydrator;

    public JpaObservationRepository(
            SpringDataObservationRepository repository,
            SpringDataObservationEvidenceRepository evidenceRepository,
            ObjectMapper mapper, ObservationRehydrator rehydrator) {
        this.repository = repository; this.evidenceRepository = evidenceRepository;
        this.mapper = mapper; this.rehydrator = rehydrator;
    }

    @Override @Transactional
    public Observation save(Observation observation) {
        repository.saveAndFlush(entity(observation));
        persistEvidence(observation);
        return observation;
    }

    @Override @Transactional
    public void supersede(Observation previous, Observation replacement) {
        if (previous.status() != ObservationStatus.SUPERSEDED
                || replacement.status() != ObservationStatus.ACTIVE
                || replacement.version() != previous.version() + 1
                || !previous.lineageId().equals(replacement.lineageId())) {
            throw new IllegalArgumentException("Invalid observation version relationship");
        }
        repository.save(entity(previous));
        repository.saveAndFlush(entity(replacement));
        persistEvidence(previous); persistEvidence(replacement);
    }

    @Override @Transactional(readOnly = true)
    public Optional<Observation> findById(UUID id) { return repository.findById(id).map(this::domain); }
    @Override @Transactional(readOnly = true)
    public List<Observation> findByInstrument(String instrument) {
        return repository.findByInstrumentIgnoreCase(instrument).stream().map(this::domain).toList();
    }
    @Override public List<Observation> findActive() { return matching(v -> v.status() == ObservationStatus.ACTIVE); }
    @Override public List<Observation> findByType(ObservationType type) { return matching(v -> v.type().equals(type)); }
    @Override public List<Observation> findByStatus(ObservationStatus status) { return matching(v -> v.status() == status); }
    @Override public List<Observation> findByHorizon(String horizon) { return matching(v -> v.horizon().equalsIgnoreCase(horizon)); }
    @Override public List<Observation> findByCategory(String category) {
        return matching(v -> v.categories().stream().anyMatch(category::equalsIgnoreCase));
    }
    @Override public List<Observation> findByConfidence(BigDecimal minimum, BigDecimal maximum) {
        if (minimum.compareTo(maximum) > 0) throw new IllegalArgumentException("Invalid confidence range");
        return matching(v -> v.confidence().score().compareTo(minimum) >= 0
                && v.confidence().score().compareTo(maximum) <= 0);
    }
    @Override public List<Observation> findByTimeRange(Instant from, Instant to) {
        if (!from.isBefore(to)) throw new IllegalArgumentException("Invalid time range");
        return matching(v -> !v.createdAt().isBefore(from) && v.createdAt().isBefore(to));
    }

    @Transactional(readOnly = true)
    protected List<Observation> matching(Predicate<Observation> predicate) {
        return repository.findAll().stream().map(this::domain).filter(predicate)
                .sorted(Comparator.comparing(Observation::createdAt).thenComparing(Observation::id))
                .toList();
    }

    private JpaObservationEntity entity(Observation value) {
        JpaObservationEntity entity = new JpaObservationEntity();
        entity.observationId = value.id(); entity.lineageId = value.lineageId();
        entity.version = value.version(); entity.instrument = value.instrument();
        entity.observationType = value.type().value(); entity.status = value.status().name();
        entity.ruleVersion = value.consolidationRuleVersion(); entity.createdAt = value.createdAt();
        Details details = new Details(
                value.title(), value.explanation(), value.categories(), value.horizon(),
                value.validFrom(), value.validUntil().orElse(null),
                value.supersedes().orElse(null), value.supersededBy().orElse(null), value.evidence());
        entity.payload = write(details);
        entity.fingerprint = fingerprint(value);
        return entity;
    }

    private Observation domain(JpaObservationEntity entity) {
        Details value = read(entity.payload, Details.class);
        return rehydrator.restore(new ObservationRehydrator.Snapshot(
                entity.observationId, entity.lineageId, entity.version, entity.instrument,
                new ObservationType(entity.observationType), ObservationStatus.valueOf(entity.status),
                value.title(), value.explanation(), value.categories(), value.horizon(),
                entity.createdAt, value.validFrom(), value.validUntil(), value.supersedes(),
                value.supersededBy(), entity.ruleVersion, value.evidence()));
    }

    private void persistEvidence(Observation observation) {
        for (ObservationEvidence evidence : observation.evidence()) {
            JpaObservationEvidenceEntity entity = new JpaObservationEvidenceEntity();
            entity.evidenceId = evidence.evidenceId(); entity.observationId = observation.id();
            entity.capabilityExecutionId = evidence.capabilityResult().capabilityExecutionId();
            entity.artifactInputFingerprint = evidence.capabilityResult().artifacts().stream()
                    .map(ArtifactTrace::inputFingerprint).sorted().findFirst()
                    .orElse(ArtifactFingerprint.empty().value());
            entity.payload = write(evidence); evidenceRepository.save(entity);
        }
    }

    private String fingerprint(Observation value) {
        List<String> components = new ArrayList<>();
        components.add(value.consolidationRuleVersion());
        value.evidence().stream().map(item -> item.capabilityResult().capabilityExecutionId()
                + ":" + item.capabilityResult().artifacts().stream()
                .map(ArtifactTrace::inputFingerprint).sorted().toList()).sorted()
                .forEach(components::add);
        return ArtifactFingerprint.ofInputs(components).value();
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize Observation", e); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot deserialize Observation", e); }
    }
    private record Details(String title, String explanation, Set<String> categories,
                           String horizon, Instant validFrom, Instant validUntil,
                           UUID supersedes, UUID supersededBy,
                           List<ObservationEvidence> evidence) { }
}
