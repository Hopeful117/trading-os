package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.feature.AnalyzerProvenance;
import com.hope.trading.market_intelligence.domain.feature.FeatureGroupStatus;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import com.hope.trading.market_intelligence.domain.feature.FeatureValue;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

@Repository
public class JpaFeatureSnapshotRepository implements FeatureSnapshotRepository {
    private final SpringDataFeatureSnapshotRepository repository;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;

    public JpaFeatureSnapshotRepository(
            SpringDataFeatureSnapshotRepository repository,
            ObjectMapper mapper,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public Optional<FeatureSnapshot> findByFingerprint(String fingerprint) {
        return repository.findBySnapshotFingerprint(fingerprint).map(this::toDomain);
    }

    @Override
    public List<FeatureSnapshot> findByPopulation(Set<UUID> marketIds, Instant startCutoffInclusive, Instant endCutoffExclusive,
                                                  String featureProjectionVersion, String evidencePolicy) {
        var entities = marketIds.isEmpty()
                ? repository.findByEvaluationCutoffGreaterThanEqualAndEvaluationCutoffLessThanAndProjectionVersionAndEvidencePolicy(
                        startCutoffInclusive, endCutoffExclusive, featureProjectionVersion, evidencePolicy)
                : repository.findByMarketIdInAndEvaluationCutoffGreaterThanEqualAndEvaluationCutoffLessThanAndProjectionVersionAndEvidencePolicy(
                        marketIds, startCutoffInclusive, endCutoffExclusive, featureProjectionVersion, evidencePolicy);
        return entities
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public FeatureSnapshot saveIfAbsent(FeatureSnapshot snapshot) {
        Optional<JpaFeatureSnapshotEntity> sameIdentity = repository.findByIdentityFingerprint(
                snapshot.identityFingerprint());
        if (sameIdentity.isPresent()) {
            FeatureSnapshot value = toDomain(sameIdentity.get());
            if (!value.hasSameSemanticContent(snapshot)) {
                throw new IllegalStateException("Feature snapshot identity has conflicting content");
            }
            return value;
        }
        Optional<JpaFeatureSnapshotEntity> existing = repository.findBySnapshotFingerprint(snapshot.fingerprint());
        if (existing.isPresent()) {
            FeatureSnapshot value = toDomain(existing.get());
            if (!value.hasSameSemanticContent(snapshot)) {
                throw new IllegalStateException("Feature snapshot fingerprint has conflicting content");
            }
            return value;
        }
        try {
            return transactions.execute(status -> toDomain(repository.saveAndFlush(from(snapshot))));
        } catch (DataIntegrityViolationException race) {
            return repository.findByIdentityFingerprint(snapshot.identityFingerprint())
                    .or(() -> repository.findBySnapshotFingerprint(snapshot.fingerprint()))
                    .map(this::toDomain)
                    .filter(value -> value.hasSameSemanticContent(snapshot))
                    .orElseThrow(() -> new IllegalStateException(
                            "Concurrent feature snapshot insert has conflicting content", race));
        }
    }

    private JpaFeatureSnapshotEntity from(FeatureSnapshot value) {
        JpaFeatureSnapshotEntity entity = new JpaFeatureSnapshotEntity();
        entity.snapshotId = value.snapshotId();
        entity.analysisExecutionId = value.analysisExecutionId();
        entity.marketId = value.marketId();
        entity.evaluationCutoff = value.evaluationCutoff();
        entity.evidencePolicy = value.evidencePolicy().name();
        entity.projectionVersion = value.featureProjectionVersion();
        entity.contextFingerprint = value.contextFingerprint();
        entity.snapshotFingerprint = value.fingerprint();
        entity.identityFingerprint = value.identityFingerprint();
        entity.createdAt = value.createdAt();
        entity.featurePayload = write(value.values().toArray(FeatureValue[]::new));
        entity.groupPayload = write(value.groups());
        entity.provenancePayload = write(value.provenance());
        return entity;
    }

    private FeatureSnapshot toDomain(JpaFeatureSnapshotEntity entity) {
        return new FeatureSnapshot(
                entity.snapshotId,
                entity.analysisExecutionId,
                entity.marketId,
                entity.evaluationCutoff,
                EvidenceAcquisitionPolicy.valueOf(entity.evidencePolicy),
                entity.projectionVersion,
                entity.contextFingerprint,
                readList(entity.featurePayload, FeatureValue[].class),
                readList(entity.groupPayload, FeatureGroupStatus[].class),
                readList(entity.provenancePayload, AnalyzerProvenance[].class),
                entity.snapshotFingerprint,
                entity.createdAt
        );
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize feature snapshot payload", exception);
        }
    }

    private <T> List<T> readList(String value, Class<T[]> type) {
        try {
            return List.of(mapper.readValue(value, type));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize feature snapshot payload", exception);
        }
    }
}
