package com.hope.trading.broker_service.broker.infrastructure.persistence;

import com.hope.trading.broker_service.broker.domain.model.BrokerModels.SnapshotCompleteness;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RiskSnapshotPersistence {
    private final RiskSnapshotVersionRepository snapshots;
    private final BrokerPositionIdentityRepository positions;

    public RiskSnapshotPersistence(RiskSnapshotVersionRepository snapshots,
            BrokerPositionIdentityRepository positions) {
        this.snapshots = snapshots;
        this.positions = positions;
    }

    @Transactional
    public long issueVersion(UUID accountId, Instant observedAt, SnapshotCompleteness completeness) {
        return snapshots.saveAndFlush(new RiskSnapshotVersionEntity(accountId, observedAt, completeness)).version();
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
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false)
    private SnapshotCompleteness completeness;
    protected RiskSnapshotVersionEntity() {}
    RiskSnapshotVersionEntity(UUID accountId, Instant observedAt, SnapshotCompleteness completeness) {
        this.brokerAccountId=accountId;this.observedAt=observedAt;this.completeness=completeness;
    }
    long version() { return snapshotVersion; }
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

interface RiskSnapshotVersionRepository extends JpaRepository<RiskSnapshotVersionEntity, Long> {}
interface BrokerPositionIdentityRepository extends JpaRepository<BrokerPositionIdentityEntity, UUID> {
    java.util.Optional<BrokerPositionIdentityEntity> findByBrokerAccountIdAndProviderAndProviderPositionReference(
            UUID brokerAccountId, String provider, String providerPositionReference);
}
