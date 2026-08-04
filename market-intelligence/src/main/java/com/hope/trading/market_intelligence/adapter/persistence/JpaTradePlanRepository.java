package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.port.TradePlanRepository;
import com.hope.trading.market_intelligence.domain.tradeplan.ExecutionParameters;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlan;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanFactory;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanId;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanStatus;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanVersion;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanningContextReference;
import com.hope.trading.market_intelligence.domain.tradeplan.TradingRationale;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaTradePlanRepository implements TradePlanRepository {
    private final SpringDataTradePlanRepository repository;
    private final ObjectMapper objectMapper;
    private final TradePlanFactory factory = new TradePlanFactory();

    public JpaTradePlanRepository(
            SpringDataTradePlanRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public TradePlan append(TradePlan plan) {
        Optional<JpaTradePlanEntity> latest = repository.findFirstByTradePlanIdOrderByVersionDesc(
                plan.id().value());
        long expected = latest.map(entity -> entity.version + 1).orElse(1L);
        if (plan.version().value() != expected) {
            throw new IllegalStateException("TradePlan versions are append-only and sequential");
        }
        try {
            repository.saveAndFlush(toEntity(plan));
            return plan;
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException(
                    "TradePlan versions are append-only and sequential", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TradePlan> find(TradePlanId id, TradePlanVersion version) {
        return repository.findById(new JpaTradePlanId(id.value(), version.value())).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TradePlan> findLatest(TradePlanId id) {
        return repository.findTopByTradePlanIdOrderByVersionDesc(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional
    public Optional<TradePlan> findLatestForUpdate(TradePlanId id) {
        return repository.findFirstByTradePlanIdOrderByVersionDesc(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TradePlan> findNext(TradePlanId id, TradePlanVersion version) {
        return repository.findFirstByTradePlanIdAndVersionGreaterThanOrderByVersionAsc(
                id.value(), version.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TradePlan> history(TradePlanId id) {
        return repository.findByTradePlanIdOrderByVersionAsc(id.value()).stream()
                .map(this::toDomain).toList();
    }

    private JpaTradePlanEntity toEntity(TradePlan plan) {
        JpaTradePlanEntity entity = new JpaTradePlanEntity();
        entity.tradePlanId = plan.id().value();
        entity.version = plan.version().value();
        entity.previousVersion = plan.previousVersion().map(TradePlanVersion::value).orElse(null);
        entity.status = plan.status().name();
        entity.tradingContextId = plan.planningContext().id();
        entity.tradingContextVersion = plan.planningContext().version();
        entity.tradingContextSnapshotAt = plan.planningContext().capturedAt();
        entity.executionPayload = write(plan.execution());
        entity.rationalePayload = write(plan.rationale());
        entity.createdAt = plan.createdAt();
        return entity;
    }

    private TradePlan toDomain(JpaTradePlanEntity entity) {
        return factory.create(
                new TradePlanId(entity.tradePlanId), new TradePlanVersion(entity.version),
                entity.previousVersion == null ? null : new TradePlanVersion(entity.previousVersion),
                TradePlanStatus.valueOf(entity.status),
                new TradePlanningContextReference(
                        entity.tradingContextId, entity.tradingContextVersion,
                        entity.tradingContextSnapshotAt),
                read(entity.executionPayload, ExecutionParameters.class),
                read(entity.rationalePayload, TradingRationale.class), entity.createdAt);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize Trade Plan", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize Trade Plan", exception);
        }
    }
}
