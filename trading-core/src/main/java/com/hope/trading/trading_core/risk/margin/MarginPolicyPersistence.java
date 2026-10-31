package com.hope.trading.trading_core.risk.margin;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Durable immutable policy/version and account-assignment boundary. */
@Repository
public class MarginPolicyPersistence {
    @PersistenceContext private EntityManager entityManager;

    public Optional<MarginPolicy> assigned(UUID accountId, String instrument, Instant at) {
        TypedQuery<MarginPolicyEntity> query = entityManager.createQuery(
                "select p from MarginPolicyEntity p, MarginPolicyAssignmentEntity a "
                        + "where a.accountId=:account and p.id=a.policyId and p.version=a.policyVersion "
                        + "and p.instrument=:instrument and a.effectiveAt<=:at order by a.effectiveAt desc, a.id desc",
                MarginPolicyEntity.class);
        return query.setParameter("account", accountId).setParameter("instrument", instrument)
                .setParameter("at", at).setMaxResults(1).getResultStream().findFirst().map(this::model);
    }

    public Optional<MarginPolicy> version(UUID policyId, String version) {
        MarginPolicyEntity entity = entityManager.find(MarginPolicyEntity.class, new MarginPolicyKey(policyId, version));
        return entity == null ? Optional.empty() : Optional.of(model(entity));
    }

    @Transactional
    public void save(MarginPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("policy is required");
        if (entityManager.find(MarginPolicyEntity.class, new MarginPolicyKey(policy.id(), policy.version())) != null)
            throw new IllegalArgumentException("policy version already exists");
        MarginPolicyEntity entity = new MarginPolicyEntity();
        entity.id = policy.id(); entity.version = policy.version(); entity.instrument = policy.instrument();
        entity.marginRate = policy.marginRate(); entity.sourceCategory = policy.sourceCategory();
        entity.sourceReference = policy.sourceReference(); entity.effectiveAt = policy.effectiveAt(); entity.expiresAt = policy.expiresAt();
        entity.configuredAt = policy.configuredAt(); entity.reportingCurrencyScale = policy.reportingCurrencyScale();
        entity.revoked = policy.revoked(); entityManager.persist(entity);
    }

    @Transactional
    public void assign(UUID accountId, UUID policyId, String policyVersion, Instant assignedAt,
                       Instant effectiveAt, String provenance) {
        assign(accountId, policyId, policyVersion, assignedAt, effectiveAt, -1, provenance);
    }

    @Transactional
    public void assign(UUID accountId, UUID policyId, String policyVersion, Instant assignedAt,
                       Instant effectiveAt, long expectedAssignmentVersion, String provenance) {
        if (accountId == null || assignedAt == null || effectiveAt == null
                || blank(provenance)
                || entityManager.find(MarginPolicyEntity.class, new MarginPolicyKey(policyId, policyVersion)) == null)
            throw new IllegalArgumentException("invalid margin policy assignment");
        var current = entityManager.createQuery("select a from MarginPolicyAssignmentEntity a where a.accountId=:account order by a.effectiveAt desc, a.id desc",
                        MarginPolicyAssignmentEntity.class).setParameter("account", accountId).setMaxResults(1)
                .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst();
        if (expectedAssignmentVersion >= 0 && (current.isEmpty() || current.get().version != expectedAssignmentVersion))
            throw new IllegalStateException("margin policy assignment version conflict");
        MarginPolicyAssignmentEntity assignment = new MarginPolicyAssignmentEntity();
        assignment.accountId = accountId; assignment.policyId = policyId; assignment.policyVersion = policyVersion;
        assignment.assignedAt = assignedAt; assignment.effectiveAt = effectiveAt;
        assignment.provenance = provenance.strip();
        entityManager.persist(assignment);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private MarginPolicy model(MarginPolicyEntity e) {
        return new MarginPolicy(e.id, e.version, e.instrument, e.marginRate, e.sourceCategory,
                e.sourceReference, e.effectiveAt, e.expiresAt, e.configuredAt, e.reportingCurrencyScale, e.revoked,
                false, false, false, false);
    }
}

record MarginPolicyKey(UUID id, String version) implements java.io.Serializable {}

@jakarta.persistence.Entity(name = "MarginPolicyEntity")
@jakarta.persistence.Table(name = "margin_policy")
@jakarta.persistence.IdClass(MarginPolicyKey.class)
@org.hibernate.annotations.Immutable
class MarginPolicyEntity {
    @jakarta.persistence.Id UUID id;
    @jakarta.persistence.Id @jakarta.persistence.Column(length = 64) String version;
    @jakarta.persistence.Column(nullable = false, length = 160) String instrument;
    @jakarta.persistence.Column(name = "margin_rate", nullable = false, precision = 38, scale = 18) BigDecimal marginRate;
    @jakarta.persistence.Column(name = "source_category", nullable = false, length = 32) String sourceCategory;
    @jakarta.persistence.Column(name = "source_reference", nullable = false, length = 255) String sourceReference;
    @jakarta.persistence.Column(name = "effective_at", nullable = false) Instant effectiveAt;
    @jakarta.persistence.Column(name = "expires_at") Instant expiresAt;
    @jakarta.persistence.Column(name = "configured_at", nullable = false) Instant configuredAt;
    @jakarta.persistence.Column(name = "reporting_currency_scale", nullable = false) int reportingCurrencyScale;
    @jakarta.persistence.Column(nullable = false) boolean revoked;
}

@jakarta.persistence.Entity(name = "MarginPolicyAssignmentEntity")
@jakarta.persistence.Table(name = "margin_policy_assignment")
class MarginPolicyAssignmentEntity {
    @jakarta.persistence.Id @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY) long id;
    @jakarta.persistence.Column(name = "account_id", nullable = false) UUID accountId;
    @jakarta.persistence.Column(name = "policy_id", nullable = false) UUID policyId;
    @jakarta.persistence.Column(name = "policy_version", nullable = false, length = 64) String policyVersion;
    @jakarta.persistence.Column(name = "assigned_at", nullable = false) Instant assignedAt;
    @jakarta.persistence.Column(name = "effective_at", nullable = false) Instant effectiveAt;
    @jakarta.persistence.Column(nullable = false, columnDefinition = "text") String provenance;
    @jakarta.persistence.Version long version;
}
