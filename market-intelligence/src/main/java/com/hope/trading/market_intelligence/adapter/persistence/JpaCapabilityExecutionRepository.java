package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.port.CapabilityExecutionRepository;
import com.hope.trading.market_intelligence.domain.capability.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Repository
public class JpaCapabilityExecutionRepository implements CapabilityExecutionRepository {
    private final SpringDataCapabilityExecutionRepository repository;
    private final ObjectMapper mapper;
    public JpaCapabilityExecutionRepository(
            SpringDataCapabilityExecutionRepository repository, ObjectMapper mapper) {
        this.repository = repository; this.mapper = mapper;
    }
    @Override @Transactional
    public CapabilityExecution save(CapabilityExecution value) {
        JpaCapabilityExecutionEntity entity = new JpaCapabilityExecutionEntity();
        entity.executionId = value.id(); entity.analysisExecutionId = value.analysisExecutionId();
        entity.executionGroupId = value.executionGroupId(); entity.capabilityId = value.capabilityId().value();
        entity.capabilityVersion = value.capabilityVersion().value(); entity.state = value.state().name();
        entity.attemptNumber = value.attemptNumber(); entity.createdAt = value.createdAt();
        entity.completedAt = value.completedAt().orElse(null);
        entity.payload = write(new Snapshot(
                value.startedAt().orElse(null), value.result().orElse(null),
                value.failure().orElse(null), value.skipReason().orElse(null),
                value.previousAttemptId().orElse(null)));
        repository.saveAndFlush(entity); return value;
    }
    @Override @Transactional(readOnly = true)
    public List<CapabilityExecution> findByAnalysisExecutionId(UUID analysisId) {
        return repository.findByAnalysisExecutionIdOrderByCreatedAtAsc(analysisId).stream()
                .map(this::domain).toList();
    }
    private CapabilityExecution domain(JpaCapabilityExecutionEntity entity) {
        Snapshot value = read(entity.payload, Snapshot.class);
        return CapabilityExecution.restore(
                entity.executionId, entity.analysisExecutionId,
                new CapabilityId(entity.capabilityId), new CapabilityVersion(entity.capabilityVersion),
                CapabilityExecutionState.valueOf(entity.state), entity.createdAt,
                value.startedAt(), entity.completedAt, value.result(), value.failure(),
                value.skipReason(), entity.executionGroupId, entity.attemptNumber,
                value.previousAttemptId());
    }
    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize CapabilityExecution", e); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot deserialize CapabilityExecution", e); }
    }
    private record Snapshot(Instant startedAt, CapabilityResult result,
                            CapabilityFailure failure, SkipReason skipReason,
                            UUID previousAttemptId) { }
}
