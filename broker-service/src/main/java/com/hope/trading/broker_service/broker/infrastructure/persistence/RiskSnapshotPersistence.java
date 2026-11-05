package com.hope.trading.broker_service.broker.infrastructure.persistence;

import com.hope.trading.broker_service.broker.domain.model.BrokerModels.SnapshotCompleteness;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.BrokerAccountSnapshotV2;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RiskSnapshotPersistence {
    private final RiskSnapshotVersionRepository snapshots;
    private final BrokerPositionIdentityRepository positions;
    private final ObjectMapper mapper;

    public RiskSnapshotPersistence(RiskSnapshotVersionRepository snapshots,
            BrokerPositionIdentityRepository positions, ObjectMapper mapper) {
        this.snapshots = snapshots;
        this.positions = positions;
        this.mapper = mapper;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RiskSnapshotPersistence(RiskSnapshotVersionRepository snapshots,
            BrokerPositionIdentityRepository positions) {
        this(snapshots, positions, new ObjectMapper().findAndRegisterModules());
    }

    @Transactional
    public long issueVersion(UUID accountId, Instant observedAt, SnapshotCompleteness completeness) {
        return snapshots.saveAndFlush(new RiskSnapshotVersionEntity(accountId, observedAt, completeness)).version();
    }

    @Transactional
    public long persist(BrokerAccountSnapshotV2 snapshot) {
        try {
            return snapshots.saveAndFlush(new RiskSnapshotVersionEntity(snapshot.brokerAccountId(),
                    snapshot.observedAt(), snapshot.completedAt(), snapshot.completeness(),
                    mapper.writeValueAsString(snapshot))).version();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Broker account snapshot cannot be serialized", exception);
        }
    }

    @Transactional(readOnly = true)
    public Optional<BrokerAccountSnapshotV2> latest(UUID accountId) {
        return snapshots.findTopByBrokerAccountIdAndSnapshotPayloadIsNotNullOrderBySnapshotVersionDesc(accountId)
                .map(RiskSnapshotVersionEntity::snapshotPayload)
                .map(payload -> {
                    try {
                        return mapper.readValue(payload, BrokerAccountSnapshotV2.class);
                    } catch (JsonProcessingException exception) {
                        throw new IllegalStateException("Persisted broker account snapshot cannot be read", exception);
                    }
                });
    }

    @Transactional
    public UUID positionId(UUID accountId, String provider, String providerReference,
            String provenance, Instant now) {
        return positions.findByBrokerAccountIdAndProviderAndProviderPositionReference(
                        accountId, provider, providerReference)
                .map(BrokerPositionIdentityEntity::positionId)
                .orElseGet(() -> createPositionId(accountId, provider, providerReference, provenance, now));
    }

    private UUID createPositionId(UUID accountId, String provider, String providerReference,
            String provenance, Instant now) {
        try {
            return positions.saveAndFlush(new BrokerPositionIdentityEntity(UUID.randomUUID(), accountId,
                    provider, providerReference, provenance, now)).positionId();
        } catch (DataIntegrityViolationException concurrentInsert) {
            return positions.findByBrokerAccountIdAndProviderAndProviderPositionReference(
                            accountId, provider, providerReference)
                    .orElseThrow(() -> concurrentInsert).positionId();
        }
    }
}

@Entity
@Table(name = "broker_risk_snapshot")
class RiskSnapshotVersionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_version") private Long snapshotVersion;
    @Column(name = "broker_account_id", nullable = false, updatable = false) private UUID brokerAccountId;
    @Column(name = "observed_at", nullable = false, updatable = false) private Instant observedAt;
    @Column(name = "completed_at", updatable = false) private Instant completedAt;
    @Column(name = "snapshot_payload", columnDefinition = "text", updatable = false) private String snapshotPayload;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false)
    private SnapshotCompleteness completeness;
    protected RiskSnapshotVersionEntity() {}
    RiskSnapshotVersionEntity(UUID accountId, Instant observedAt, SnapshotCompleteness completeness) {
        this.brokerAccountId=accountId;this.observedAt=observedAt;this.completeness=completeness;
    }
    RiskSnapshotVersionEntity(UUID accountId, Instant observedAt, Instant completedAt,
            SnapshotCompleteness completeness, String snapshotPayload) {
        this(accountId, observedAt, completeness);
        this.completedAt = completedAt;
        this.snapshotPayload = snapshotPayload;
    }
    long version() { return snapshotVersion; }
    String snapshotPayload() { return snapshotPayload; }
}

@Entity
@Table(name = "broker_position_identity", uniqueConstraints = @UniqueConstraint(
        columnNames = {"broker_account_id", "provider", "provider_position_reference"}))
class BrokerPositionIdentityEntity {
    @Id @Column(name = "position_id") private UUID positionId;
    @Column(name = "broker_account_id", nullable = false, updatable = false) private UUID brokerAccountId;
    @Column(nullable = false, updatable = false) private String provider;
    @Column(name = "provider_position_reference", nullable = false, updatable = false) private String providerPositionReference;
    @Column(name = "provider_reference_provenance", nullable = false, updatable = false) private String providerReferenceProvenance;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected BrokerPositionIdentityEntity() {}
    BrokerPositionIdentityEntity(UUID id, UUID accountId, String provider, String reference,
            String provenance, Instant createdAt) {
        this.positionId=id;this.brokerAccountId=accountId;this.provider=provider;
        this.providerPositionReference=reference;this.providerReferenceProvenance=provenance;this.createdAt=createdAt;
    }
    UUID positionId() { return positionId; }
}

interface RiskSnapshotVersionRepository extends JpaRepository<RiskSnapshotVersionEntity, Long> {
    Optional<RiskSnapshotVersionEntity> findTopByBrokerAccountIdAndSnapshotPayloadIsNotNullOrderBySnapshotVersionDesc(
            UUID brokerAccountId);
}
interface BrokerPositionIdentityRepository extends JpaRepository<BrokerPositionIdentityEntity, UUID> {
    java.util.Optional<BrokerPositionIdentityEntity> findByBrokerAccountIdAndProviderAndProviderPositionReference(
            UUID brokerAccountId, String provider, String providerPositionReference);
}
