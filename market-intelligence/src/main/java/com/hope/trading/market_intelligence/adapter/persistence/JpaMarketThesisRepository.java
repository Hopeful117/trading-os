package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidence;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisScenario;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class JpaMarketThesisRepository implements MarketThesisRepository {
    private final SpringDataMarketThesisRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private static final ConcurrentHashMap<String, Object> INSERT_LOCKS = new ConcurrentHashMap<>();
    @PersistenceContext
    private EntityManager entityManager;

    public JpaMarketThesisRepository(
            SpringDataMarketThesisRepository repository, ObjectMapper mapper, Clock clock,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public MarketThesis save(MarketThesis thesis) {
        JpaMarketThesisEntity value = entity(thesis);
        entityManager.persist(value);
        entityManager.flush();
        return thesis;
    }

    @Override
    public MarketThesis saveIfAbsent(MarketThesis thesis) {
        Object lock = INSERT_LOCKS.computeIfAbsent(
                thesis.inputFingerprint(), unused -> new Object());
        synchronized (lock) {
            Optional<MarketThesis> existing = repository.findByInputFingerprint(
                    thesis.inputFingerprint()).map(this::domain);
            if (existing.isPresent()) return existing.get();
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    entityManager.persist(entity(thesis));
                    entityManager.flush();
                });
                return thesis;
            } catch (RuntimeException exception) {
                entityManager.clear();
                return repository.findByInputFingerprint(thesis.inputFingerprint())
                        .map(this::domain)
                        .orElseThrow(() -> exception);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MarketThesis> findById(UUID id) {
        return repository.findById(id).map(this::domain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MarketThesis> findByIdAndVersion(UUID id, long version) {
        return repository.findByThesisIdAndVersion(id, version).map(this::domain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MarketThesis> findByInputFingerprint(String inputFingerprint) {
        return repository.findByInputFingerprint(inputFingerprint).map(this::domain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MarketThesis> findLatest(UUID marketId, String instrument, String direction) {
        return repository.findLatestValid(marketId, instrument, direction.toUpperCase(), clock.instant(),
                        org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst().map(this::domain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarketThesis> findByMarket(UUID marketId) {
        return repository.findByMarketIdOrderByEvaluatedAtDesc(marketId).stream()
                .map(this::domain).toList();
    }

    private JpaMarketThesisEntity entity(MarketThesis thesis) {
        JpaMarketThesisEntity entity = new JpaMarketThesisEntity();
        entity.thesisId = thesis.id();
        entity.lineageId = thesis.lineageId();
        entity.version = thesis.version();
        entity.marketId = thesis.marketId();
        entity.instrument = thesis.instrument();
        entity.state = thesis.state().name();
        entity.scenarioDirection = thesis.scenario()
                .map(value -> value.direction().name()).orElse("NONE");
        entity.composerVersion = thesis.composerVersion();
        entity.inputFingerprint = thesis.inputFingerprint();
        entity.analysisExecutionId = thesis.analysisExecutionId().orElse(null);
        entity.evaluatedAt = thesis.evaluatedAt();
        entity.sourceCutoff = thesis.sourceCutoff();
        entity.validFrom = thesis.validFrom();
        entity.validUntil = thesis.validUntil().orElse(null);
        entity.supersedes = thesis.supersedes().orElse(null);
        entity.payload = write(new Details(
                thesis.scenario().orElse(null), thesis.evidence(), thesis.reasons()));
        return entity;
    }

    private MarketThesis domain(JpaMarketThesisEntity entity) {
        Details details = read(entity.payload);
        return new MarketThesis(
                entity.thesisId, entity.lineageId, entity.version, entity.marketId,
                entity.instrument, com.hope.trading.market_intelligence.domain.thesis.MarketThesisState
                        .valueOf(entity.state), details.scenario(), details.evidence(), details.reasons(),
                entity.evaluatedAt, entity.sourceCutoff, entity.validFrom, entity.validUntil,
                entity.analysisExecutionId, entity.composerVersion, entity.inputFingerprint,
                entity.supersedes);
    }

    private String write(Details details) {
        try {
            return objectMapper().writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize Market Thesis", exception);
        }
    }

    private Details read(String payload) {
        try {
            return objectMapper().readValue(payload, Details.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize Market Thesis", exception);
        }
    }

    private ObjectMapper objectMapper() {
        return mapper.copy().setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    private record Details(
            MarketThesisScenario scenario,
            List<MarketThesisEvidence> evidence,
            List<String> reasons
    ) { }
}
