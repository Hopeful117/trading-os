package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.port.ResearchExecutionRepository;
import com.hope.trading.market_intelligence.domain.research.ResearchExecution;
import com.hope.trading.market_intelligence.domain.research.ResearchExecutionStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaResearchExecutionRepository implements ResearchExecutionRepository {
    private final SpringDataResearchExecutionRepository repository;
    private final ObjectMapper mapper;

    public JpaResearchExecutionRepository(SpringDataResearchExecutionRepository repository,
                                           ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public ResearchExecution save(ResearchExecution value) {
        JpaResearchExecutionEntity entity = new JpaResearchExecutionEntity();
        entity.researchId = value.researchId();
        entity.actorId = value.actorId();
        entity.idempotencyKey = value.idempotencyKey();
        entity.requestFingerprint = value.requestFingerprint();
        entity.marketIds = write(value.marketIds());
        entity.objective = value.objective();
        entity.depth = value.depth();
        entity.status = value.status().name();
        entity.requestedAt = value.requestedAt();
        entity.updatedAt = value.updatedAt();
        entity.completedAt = value.completedAt();
        entity.failureCode = value.failureCode();
        entity.report = value.report() == null ? null : write(value.report());
        try {
            repository.saveAndFlush(entity);
            return value;
        } catch (DataIntegrityViolationException race) {
            if (!isIdempotencyConstraintViolation(race)) {
                throw race;
            }
            return repository.findByActorIdAndIdempotencyKey(value.actorId(), value.idempotencyKey())
                    .map(this::domain)
                    .orElseThrow(() -> new IllegalStateException(
                            "Concurrent research insert has no durable idempotent result", race));
        }
    }

    @Override
    public Optional<ResearchExecution> findById(UUID researchId) {
        return repository.findById(researchId).map(this::domain);
    }

    @Override
    public Optional<ResearchExecution> findByActorIdAndIdempotencyKey(UUID actorId, String idempotencyKey) {
        return repository.findByActorIdAndIdempotencyKey(actorId, idempotencyKey).map(this::domain);
    }

    @Override
    public List<ResearchExecution> findByStatus(ResearchExecutionStatus status) {
        return repository.findByStatus(status.name()).stream().map(this::domain).toList();
    }

    @Override
    @Transactional
    public boolean saveIfStatus(ResearchExecution value, ResearchExecutionStatus expectedStatus) {
        JpaResearchExecutionEntity entity = repository.findForUpdate(value.researchId()).orElse(null);
        if (entity == null || !expectedStatus.name().equals(entity.status)) {
            return false;
        }
        copy(value, entity);
        repository.saveAndFlush(entity);
        return true;
    }

    @Override
    @Transactional
    public Optional<ResearchExecution> claimRequested(UUID researchId, java.time.Instant now) {
        return repository.claimRequested(researchId, now) == 1
                ? repository.findById(researchId).map(this::domain)
                : Optional.empty();
    }

    private ResearchExecution domain(JpaResearchExecutionEntity entity) {
        return ResearchExecution.restore(
                entity.researchId, entity.actorId, entity.idempotencyKey, entity.requestFingerprint,
                read(entity.marketIds, new TypeReference<>() {}), entity.objective, entity.depth,
                ResearchExecutionStatus.valueOf(entity.status), entity.requestedAt, entity.updatedAt,
                entity.completedAt, entity.failureCode,
                entity.report == null ? null : read(entity.report, JsonNode.class));
    }

    private void copy(ResearchExecution value, JpaResearchExecutionEntity entity) {
        entity.researchId = value.researchId();
        entity.actorId = value.actorId();
        entity.idempotencyKey = value.idempotencyKey();
        entity.requestFingerprint = value.requestFingerprint();
        entity.marketIds = write(value.marketIds());
        entity.objective = value.objective();
        entity.depth = value.depth();
        entity.status = value.status().name();
        entity.requestedAt = value.requestedAt();
        entity.updatedAt = value.updatedAt();
        entity.completedAt = value.completedAt();
        entity.failureCode = value.failureCode();
        entity.report = value.report() == null ? null : write(value.report());
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize research execution", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize research execution", exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize research execution", exception);
        }
    }

    private boolean isIdempotencyConstraintViolation(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("uk_research_actor_idempotency")
                || (normalized.contains("actor_id") && normalized.contains("idempotency_key"));
    }
}
