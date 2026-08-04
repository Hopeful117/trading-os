package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.port.ArtifactPersistencePort;
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
        this.repository = repository; this.mapper = mapper;
    }
    @Override @Transactional(readOnly = true)
    public List<ProducedArtifact> find(UUID analysisId, ArtifactType type, ArtifactVersion version) {
        return repository.findByAnalysisExecutionIdAndArtifactTypeAndArtifactVersion(
                analysisId, type.value(), version.value()).stream()
                .map(entity -> read(entity.payload, ProducedArtifact.class)).toList();
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
            if (!existing.get().payload.equals(payload)) {
                throw new IllegalStateException("Artifact identity has conflicting payload");
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
    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize Artifact", e); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot deserialize Artifact", e); }
    }
}
