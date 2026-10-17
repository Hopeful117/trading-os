package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.port.ArtifactPersistencePort;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactProvenance;
import com.hope.trading.market_intelligence.domain.artifact.StoredArtifact;
import com.hope.trading.market_intelligence.domain.capability.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Repository
public class JpaCapabilityArtifactPersistenceAdapter implements ArtifactPersistencePort {
    private final SpringDataCapabilityArtifactRepository repository;
    private final ObjectMapper mapper;
    public JpaCapabilityArtifactPersistenceAdapter(
            SpringDataCapabilityArtifactRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper.copy().setVisibility(
                PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }
    @Override @Transactional(readOnly = true)
    public List<ProducedArtifact> find(UUID analysisId, ArtifactType type, ArtifactVersion version) {
        return repository.findByAnalysisExecutionIdAndArtifactTypeAndArtifactVersion(
                analysisId, type.value(), version.value()).stream()
                .map(entity -> linkedToAnalysis(
                        read(entity.payload, ProducedArtifact.class), entity.analysisExecutionId)).toList();
    }
    @Override @Transactional
    public void save(UUID analysisId, ProducedArtifact artifact) {
        var key = artifact.artifact().key();
        String payload = write(artifact);
        var existing = repository
                .findByAnalysisExecutionIdAndArtifactTypeAndArtifactVersionAndParametersFingerprintAndInputFingerprint(
                        analysisId, artifact.type().value(), artifact.version().value(),
                        key.parametersFingerprint().value(), key.inputFingerprint().value());
        if (existing.isPresent()) {
            JpaCapabilityArtifactEntity entity = existing.get();
            ProducedArtifact persisted = read(entity.payload, ProducedArtifact.class);
            ProducedArtifact merged = mergeReuseProvenance(persisted, artifact);
            String mergedPayload = write(merged);
            if (!entity.payload.equals(mergedPayload)) {
                entity.payload = mergedPayload;
                entity.producingExecutionId = merged.artifact().provenance().producingExecutionId();
                entity.producedAt = merged.artifact().provenance().producedAt();
                repository.saveAndFlush(entity);
            }
            return;
        }
        JpaCapabilityArtifactEntity entity = new JpaCapabilityArtifactEntity();
        entity.rowId = UUID.randomUUID(); entity.analysisExecutionId = analysisId;
        entity.artifactType = artifact.type().value(); entity.artifactVersion = artifact.version().value();
        entity.parametersFingerprint = key.parametersFingerprint().value();
        entity.inputFingerprint = key.inputFingerprint().value();
        entity.producingExecutionId = artifact.artifact().provenance().producingExecutionId();
        entity.producedAt = artifact.artifact().provenance().producedAt(); entity.payload = payload;
        repository.saveAndFlush(entity);
    }

    private ProducedArtifact mergeReuseProvenance(
            ProducedArtifact persisted, ProducedArtifact incoming) {
        var persistedProvenance = persisted.artifact().provenance();
        var incomingProvenance = incoming.artifact().provenance();
        Set<UUID> reusedBy = new HashSet<>(persistedProvenance.reusedByExecutions());
        reusedBy.addAll(incomingProvenance.reusedByExecutions());
        var mergedPersistedProvenance = new ArtifactProvenance(
                persistedProvenance.producerId(), persistedProvenance.producerVersion(),
                persistedProvenance.producingExecutionId(), persistedProvenance.producedAt(),
                persistedProvenance.inputArtifacts(), reusedBy,
                persistedProvenance.contextFingerprint(), persistedProvenance.evidenceFingerprint());
        var mergedIncomingProvenance = new ArtifactProvenance(
                incomingProvenance.producerId(), incomingProvenance.producerVersion(),
                incomingProvenance.producingExecutionId(), incomingProvenance.producedAt(),
                incomingProvenance.inputArtifacts(), reusedBy,
                incomingProvenance.contextFingerprint(), incomingProvenance.evidenceFingerprint());
        if (!sameExceptReuse(persisted, mergedPersistedProvenance, incoming, mergedIncomingProvenance)) {
            throw new IllegalStateException("Artifact identity has conflicting payload");
        }
        StoredArtifact stored = persisted.artifact();
        return new ProducedArtifact(persisted.type(), persisted.version(),
                new StoredArtifact(stored.key(), stored.content(), stored.freshness(),
                        mergedPersistedProvenance, stored.quality(), stored.version()));
    }

    private ProducedArtifact linkedToAnalysis(ProducedArtifact artifact, UUID analysisId) {
        StoredArtifact stored = artifact.artifact();
        return new ProducedArtifact(artifact.type(), artifact.version(),
                new StoredArtifact(stored.key(), stored.content(), stored.freshness(),
                        stored.provenance().reusedBy(analysisId), stored.quality(), stored.version()));
    }

    private boolean sameExceptReuse(
            ProducedArtifact persisted, ArtifactProvenance persistedProvenance,
            ProducedArtifact incoming, ArtifactProvenance incomingProvenance) {
        StoredArtifact persistedArtifact = persisted.artifact();
        StoredArtifact incomingArtifact = incoming.artifact();
        StoredArtifact normalizedPersisted = new StoredArtifact(
                persistedArtifact.key(), persistedArtifact.content(), persistedArtifact.freshness(),
                persistedProvenance, persistedArtifact.quality(), persistedArtifact.version());
        StoredArtifact normalizedIncoming = new StoredArtifact(
                incomingArtifact.key(), incomingArtifact.content(), incomingArtifact.freshness(),
                incomingProvenance, incomingArtifact.quality(), incomingArtifact.version());
        return persisted.type().equals(incoming.type())
                && persisted.version().equals(incoming.version())
                && normalizedPersisted.equals(normalizedIncoming);
    }
    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize Artifact", e); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot deserialize Artifact", e); }
    }
}
