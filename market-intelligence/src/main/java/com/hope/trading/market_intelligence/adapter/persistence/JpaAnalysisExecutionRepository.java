package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.execution.*;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Override
    public AnalysisExecution save(AnalysisExecution value) {
        JpaAnalysisExecutionEntity entity = new JpaAnalysisExecutionEntity();
        entity.executionId = value.executionId(); entity.idempotencyKey = value.idempotencyKey().value();
        entity.status = value.status().name(); entity.requestedAt = value.requestedAt();
        entity.updatedAt = value.updatedAt(); entity.expiresAt = value.expiresAt();
        entity.completedAt = value.completedAt().orElse(null);
        entity.marketId = value.provenance().marketId(); entity.mode = value.provenance().mode().name();
        entity.evaluationCutoff = value.provenance().evaluationCutoff();
        entity.evidencePolicy = value.provenance().evidencePolicy().name();
        entity.payload = write(snapshot(value));
        try {
            repository.saveAndFlush(entity);
            return value;
        } catch (DataIntegrityViolationException race) {
            return repository.findByIdempotencyKey(value.idempotencyKey().value())
                    .map(this::domain)
                    .orElseThrow(() -> new IllegalStateException(
                            "Concurrent execution insert has no durable idempotent result", race));
        }
    }
    @Override @Transactional(readOnly = true)
    public Optional<AnalysisExecution> findById(UUID id) { return repository.findById(id).map(this::domain); }
    @Override @Transactional(readOnly = true)
    public List<AnalysisExecution> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findByExecutionIdIn(ids).stream().map(this::domain).toList();
    }

    @Override @Transactional(readOnly = true)
    public Optional<AnalysisExecution> findLatestByMarketId(UUID marketId) {
        return repository.findFirstByMarketIdOrderByUpdatedAtDesc(marketId).map(this::domain);
    }
    @Override @Transactional(readOnly = true)
    public Optional<AnalysisExecution> findReusable(IdempotencyKey key, Instant now) {
        return repository.findByIdempotencyKey(key.value()).map(this::domain)
                .filter(value -> !value.isExpiredAt(now));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnalysisExecution> findByIdempotencyKey(IdempotencyKey key) {
        return repository.findByIdempotencyKey(key.value()).map(this::domain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisExecution> findByResearchScope(
            Collection<UUID> marketIds,
            Instant startCutoffInclusive,
            Instant endCutoffExclusive,
            EvidenceAcquisitionPolicy evidencePolicy
    ) {
        if (marketIds.isEmpty()) {
            return List.of();
        }
        return repository
                .findByMarketIdInAndEvaluationCutoffGreaterThanEqualAndEvaluationCutoffLessThanAndEvidencePolicy(
                        marketIds, startCutoffInclusive, endCutoffExclusive, evidencePolicy.name())
                .stream().map(this::domain).toList();
    }

    @Override
    @Transactional
    public boolean transitionStatus(
            UUID executionId,
            AnalysisExecutionStatus expected,
            AnalysisExecutionStatus target,
            Instant updatedAt
    ) {
        return repository.transitionStatus(
                executionId,
                expected.name(),
                target.name(),
                updatedAt
        ) == 1;
    }

    private Snapshot snapshot(AnalysisExecution value) {
        return new Snapshot(
                value.resultQuality().orElse(null), value.failureCode().orElse(null), value.executionPolicy(), value.capabilities(),
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
                value.provenance(), value.traceMetadata(), value.result(), value.failureCode());
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
            AnalysisResultQuality resultQuality, String failureCode, AnalysisExecutionPolicy policy,
            List<String> capabilities, RetryMetadata retryMetadata,
            AnalysisExecutionProvenance provenance, AnalysisTraceMetadata traceMetadata,
            ConsolidatedIntelligence result) { }
}
