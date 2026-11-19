package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.port.SignalReplayResultRepository;
import com.hope.trading.market_intelligence.domain.replay.SignalReplayResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Repository
public class JpaSignalReplayResultRepository implements SignalReplayResultRepository {
    private final SpringDataSignalReplayResultRepository repository;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;

    public JpaSignalReplayResultRepository(
            SpringDataSignalReplayResultRepository repository,
            ObjectMapper mapper,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public Optional<SignalReplayResult> findByFingerprint(String fingerprint) {
        return repository.findByResultFingerprint(fingerprint).map(this::toDomain);
    }

    @Override
    public SignalReplayResult saveIfAbsent(SignalReplayResult result) {
        Optional<JpaSignalReplayResultEntity> existing = repository.findByResultFingerprint(result.fingerprint());
        if (existing.isPresent()) {
            SignalReplayResult value = toDomain(existing.get());
            ensureSame(value, result);
            return value;
        }
        try {
            return transactions.execute(status -> toDomain(repository.saveAndFlush(fromDomain(result))));
        } catch (DataIntegrityViolationException race) {
            return repository.findByResultFingerprint(result.fingerprint())
                    .map(this::toDomain)
                    .filter(value -> value.hasSameSemanticContent(result))
                    .orElseThrow(() -> new IllegalStateException(
                            "Concurrent signal replay insert has conflicting content", race));
        }
    }

    private JpaSignalReplayResultEntity fromDomain(SignalReplayResult result) {
        JpaSignalReplayResultEntity entity = new JpaSignalReplayResultEntity();
        entity.resultId = result.resultId();
        entity.resultFingerprint = result.fingerprint();
        entity.marketId = result.marketId();
        entity.product = result.product();
        entity.instrument = result.instrument();
        entity.provider = result.provider();
        entity.timeframe = result.timeframe().name();
        entity.evaluationCutoff = result.evaluationCutoff();
        entity.strategyId = result.strategyId().value();
        entity.strategyVersion = result.strategyVersion();
        entity.evidencePolicy = result.evidencePolicy().name();
        entity.evidenceStatus = result.evidenceStatus().name();
        entity.evidenceFingerprint = result.evidenceFingerprint();
        entity.contextFingerprint = result.contextFingerprint();
        entity.costAssumptionVersion = result.costAssumptions().version();
        entity.evaluationStatus = result.evaluationStatus().name();
        entity.direction = result.direction() == null ? null : result.direction().name();
        entity.spread = result.costAssumptions().spread();
        entity.transactionCost = result.costAssumptions().transactionCost();
        entity.evidenceSampleCount = result.evidenceSamples().size();
        entity.payload = serialize(result);
        entity.createdAt = result.createdAt();
        return entity;
    }

    private SignalReplayResult toDomain(JpaSignalReplayResultEntity entity) {
        try {
            return mapper.readValue(entity.payload, SignalReplayResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize signal replay result", exception);
        }
    }

    private String serialize(SignalReplayResult result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize signal replay result", exception);
        }
    }

    private void ensureSame(SignalReplayResult existing, SignalReplayResult requested) {
        if (!existing.hasSameSemanticContent(requested)) {
            throw new IllegalStateException("Signal replay fingerprint has conflicting content");
        }
    }
}
