package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence;
import com.hope.trading.market_intelligence.domain.execution.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Repository
public class JpaAnalysisExecutionRepository implements AnalysisExecutionRepository {
    private final SpringDataAnalysisExecutionRepository repository;
    private final ObjectMapper mapper;
    public JpaAnalysisExecutionRepository(
            SpringDataAnalysisExecutionRepository repository, ObjectMapper mapper) {
        this.repository = repository; this.mapper = mapper;
    }

    @Override @Transactional
    public AnalysisExecution save(AnalysisExecution value) {
        JpaAnalysisExecutionEntity entity = new JpaAnalysisExecutionEntity();
        entity.executionId = value.executionId(); entity.idempotencyKey = value.idempotencyKey().value();
        entity.status = value.status().name(); entity.requestedAt = value.requestedAt();
        entity.updatedAt = value.updatedAt(); entity.expiresAt = value.expiresAt();
        entity.completedAt = value.completedAt().orElse(null);
        entity.marketId = value.provenance().marketId(); entity.mode = value.provenance().mode().name();
        entity.payload = write(snapshot(value)); repository.saveAndFlush(entity); return value;
    }
    @Override @Transactional(readOnly = true)
    public Optional<AnalysisExecution> findById(UUID id) { return repository.findById(id).map(this::domain); }
    @Override @Transactional(readOnly = true)
    public Optional<AnalysisExecution> findReusable(IdempotencyKey key, Instant now) {
        return repository.findByIdempotencyKey(key.value()).map(this::domain)
                .filter(value -> !value.isExpiredAt(now));
    }

    private Snapshot snapshot(AnalysisExecution value) {
        return new Snapshot(
                value.resultQuality().orElse(null), value.executionPolicy(), value.capabilities(),
                value.retryMetadata(), value.provenance(), value.traceMetadata(),
                value.result().orElse(null));
    }
    private AnalysisExecution domain(JpaAnalysisExecutionEntity entity) {
        Snapshot value = read(entity.payload, Snapshot.class);
        return AnalysisExecution.restore(
                entity.executionId, new IdempotencyKey(entity.idempotencyKey),
                AnalysisExecutionStatus.valueOf(entity.status), value.resultQuality(),
                value.policy(), entity.requestedAt, entity.updatedAt, entity.expiresAt,
                entity.completedAt, value.capabilities(), value.retryMetadata(),
                value.provenance(), value.traceMetadata(), value.result());
    }
    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize AnalysisExecution", e); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot deserialize AnalysisExecution", e); }
    }
    private record Snapshot(
            AnalysisResultQuality resultQuality, AnalysisExecutionPolicy policy,
            List<String> capabilities, RetryMetadata retryMetadata,
            AnalysisExecutionProvenance provenance, AnalysisTraceMetadata traceMetadata,
            ConsolidatedIntelligence result) { }
}
