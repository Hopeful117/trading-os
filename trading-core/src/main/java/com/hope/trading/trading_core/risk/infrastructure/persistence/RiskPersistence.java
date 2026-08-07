package com.hope.trading.trading_core.risk.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.risk.application.RiskEvaluationModels.Response;
import com.hope.trading.trading_core.model.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.transaction.Transactional;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Repository;
import org.hibernate.annotations.Immutable;

@Repository
public class RiskPersistence {
    private final EntityManager entityManager;
    private final ObjectMapper mapper;

    public RiskPersistence(EntityManager entityManager, ObjectMapper mapper) {
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    public Optional<StoredEvaluation> evaluation(UUID actorId, String key) {
        return entityManager.createQuery("select e from RiskEvaluationEntity e where e.actorId=:actor and e.idempotencyKey=:key",
                        RiskEvaluationEntity.class).setParameter("actor", actorId).setParameter("key", key)
                .getResultStream().findFirst().map(e -> new StoredEvaluation(e.id, e.tradePlanId,
                        e.tradePlanVersion, e.accountId, e.status, e.decision,
                        read(e.responsePayload, Response.class)));
    }

    public Optional<AccountConfiguration> configuration(UUID accountId) {
        AccountRiskConfigurationEntity value = entityManager.find(AccountRiskConfigurationEntity.class, accountId);
        return value == null ? Optional.empty() : Optional.of(new AccountConfiguration(value.accountId,
                value.brokerAccountId, value.riskTimeZone, value.reportingCurrency, value.portfolioId));
    }

    public Optional<Profile> assignedProfile(UUID accountId) {
        AccountRiskProfileAssignmentEntity assignment = entityManager.find(AccountRiskProfileAssignmentEntity.class, accountId);
        if (assignment == null) return Optional.empty();
        RiskProfileEntity profile = entityManager.find(RiskProfileEntity.class,
                new ProfileKey(assignment.profileId, assignment.profileSemanticVersion));
        if (profile == null) return Optional.empty();
        List<ProfileRule> rules = entityManager.createQuery(
                        "select r from RiskProfileRuleEntity r where r.profileId=:id and r.profileSemanticVersion=:version",
                        RiskProfileRuleEntity.class).setParameter("id", profile.id)
                .setParameter("version", profile.semanticVersion).getResultList().stream()
                .map(r -> new ProfileRule(r.ruleId, r.ruleVersion, r.category, r.severity,
                        r.priority, r.maximumRatio, r.provenance)).toList();
        return Optional.of(new Profile(profile.id, profile.semanticVersion, profile.policyId,
                profile.policyVersion, profile.authority, profile.createdAt, profile.provenance,
                assignment.assignedAt, assignment.provenance, rules));
    }

    public long component(UUID evaluationId, String type, String sourceVersion,
                          Instant capturedAt, String payload) {
        RiskComponentSnapshotEntity entity = new RiskComponentSnapshotEntity();
        entity.evaluationId = evaluationId; entity.componentType = type; entity.sourceVersion = sourceVersion;
        entity.capturedAt = capturedAt; entity.payloadSchemaVersion = 1; entity.payload = payload;
        entityManager.persist(entity); entityManager.flush();
        return entity.version;
    }

    public long context(UUID evaluationId, Instant capturedAt, String payload) {
        RiskContextSnapshotEntity entity = new RiskContextSnapshotEntity();
        entity.evaluationId = evaluationId; entity.capturedAt = capturedAt;
        entity.payloadSchemaVersion = 1; entity.payload = payload;
        entityManager.persist(entity); entityManager.flush();
        return entity.version;
    }

    public Baseline baseline(UUID accountId, LocalDate riskDay, Instant startsAt, Instant endsAt,
                             String currency, BigDecimal amount, String payload) {
        if (entityManager.find(Account.class, accountId, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new IllegalStateException("Account does not exist for risk-day baseline");
        }
        Optional<RiskDayBaselineEntity> existing = entityManager.createQuery(
                        "select b from RiskDayBaselineEntity b where b.accountId=:account and b.riskDay=:day",
                        RiskDayBaselineEntity.class).setParameter("account", accountId)
                .setParameter("day", riskDay).getResultStream().findFirst();
        if (existing.isPresent()) return baseline(existing.get());
        RiskDayBaselineEntity entity = new RiskDayBaselineEntity();
        entity.accountId = accountId; entity.riskDay = riskDay; entity.startsAt = startsAt; entity.endsAt = endsAt;
        entity.reportingCurrency = currency; entity.amount = amount; entity.payloadSchemaVersion = 1; entity.payload = payload;
        entityManager.persist(entity); entityManager.flush();
        return baseline(entity);
    }

    private Baseline baseline(RiskDayBaselineEntity entity) {
        return new Baseline(entity.version, entity.amount, entity.reportingCurrency, entity.startsAt,
                entity.endsAt, entity.payloadSchemaVersion, entity.payload);
    }

    public void evaluation(UUID id, UUID actorId, String key, UUID tradePlanId, long tradePlanVersion,
                           UUID accountId, Instant requestedAt, String status, String decision,
                           Long contextVersion, Object officialResult, Response response) {
        RiskEvaluationEntity entity = new RiskEvaluationEntity();
        entity.id = id; entity.actorId = actorId; entity.idempotencyKey = key;
        entity.tradePlanId = tradePlanId; entity.tradePlanVersion = tradePlanVersion; entity.accountId = accountId;
        entity.requestedAt = requestedAt; entity.status = status; entity.decision = decision;
        entity.contextSnapshotVersion = contextVersion; entity.resultSchemaVersion = 1;
        entity.resultPayload = write(officialResult); entity.responseSchemaVersion = 1;
        entity.responsePayload = write(response); entityManager.persist(entity); entityManager.flush();
    }

    public void acknowledgment(UUID evaluationId, UUID tradePlanId, long tradePlanVersion,
                               String decision, Instant evaluatedAt, Instant now) {
        RiskAcknowledgmentOutboxEntity entity = new RiskAcknowledgmentOutboxEntity();
        entity.evaluationId = evaluationId; entity.tradePlanId = tradePlanId;
        entity.tradePlanVersion = tradePlanVersion; entity.decision = decision;
        entity.evaluatedAt = evaluatedAt; entity.status = "PENDING"; entity.attemptCount = 0;
        entity.nextAttemptAt = now; entity.createdAt = now; entity.updatedAt = now;
        entityManager.persist(entity); entityManager.flush();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Optional<AcknowledgmentDelivery> claimAcknowledgment(UUID evaluationId, Instant now,
                                                                 boolean ignoreSchedule) {
        Optional<RiskAcknowledgmentOutboxEntity> found = entityManager.createQuery(
                        "select a from RiskAcknowledgmentOutboxEntity a where a.evaluationId=:evaluation", RiskAcknowledgmentOutboxEntity.class)
                .setParameter("evaluation", evaluationId).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream().findFirst();
        if (found.isEmpty()) return Optional.empty();
        RiskAcknowledgmentOutboxEntity entity = found.get();
        boolean expiredClaim = "PROCESSING".equals(entity.status)
                && entity.leaseUntil != null && !entity.leaseUntil.isAfter(now);
        boolean due = entity.nextAttemptAt == null || !entity.nextAttemptAt.isAfter(now);
        if ("DELIVERED".equals(entity.status) || (!expiredClaim && "PROCESSING".equals(entity.status))
                || (!ignoreSchedule && !due)) return Optional.empty();
        UUID token = UUID.randomUUID();
        entity.status = "PROCESSING"; entity.claimToken = token;
        entity.leaseUntil = now.plus(1, ChronoUnit.MINUTES); entity.attemptCount++;
        entity.updatedAt = now; entityManager.flush();
        return Optional.of(new AcknowledgmentDelivery(entity.evaluationId, entity.tradePlanId,
                entity.tradePlanVersion, entity.decision, entity.evaluatedAt, token));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void acknowledgmentDelivered(UUID evaluationId, UUID token, Instant now) {
        RiskAcknowledgmentOutboxEntity entity = claimedAcknowledgment(evaluationId, token);
        if (entity == null) return;
        entity.status = "DELIVERED"; entity.deliveredAt = now; entity.nextAttemptAt = null;
        entity.claimToken = null; entity.leaseUntil = null; entity.lastError = null; entity.updatedAt = now;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void acknowledgmentFailed(UUID evaluationId, UUID token, Instant now, String error) {
        RiskAcknowledgmentOutboxEntity entity = claimedAcknowledgment(evaluationId, token);
        if (entity == null) return;
        long delaySeconds = Math.min(300, 1L << Math.min(entity.attemptCount, 8));
        entity.status = "PENDING"; entity.nextAttemptAt = now.plusSeconds(delaySeconds);
        entity.claimToken = null; entity.leaseUntil = null;
        entity.lastError = error == null ? "Remote acknowledgment failed" : error.substring(0, Math.min(500, error.length()));
        entity.updatedAt = now;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<UUID> dueAcknowledgments(Instant now, int limit) {
        return entityManager.createQuery("select a.evaluationId from RiskAcknowledgmentOutboxEntity a "
                        + "where (a.status='PENDING' and a.nextAttemptAt<=:now) "
                        + "or (a.status='PROCESSING' and a.leaseUntil<=:now) order by a.nextAttemptAt", UUID.class)
                .setParameter("now", now).setMaxResults(limit).getResultList();
    }

    private RiskAcknowledgmentOutboxEntity claimedAcknowledgment(UUID evaluationId, UUID token) {
        RiskAcknowledgmentOutboxEntity entity = entityManager.find(RiskAcknowledgmentOutboxEntity.class,
                evaluationId, LockModeType.PESSIMISTIC_WRITE);
        return entity != null && "PROCESSING".equals(entity.status) && token.equals(entity.claimToken) ? entity : null;
    }

    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("Risk provenance serialization failed", failure); }
    }

    private <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (Exception failure) { throw new IllegalStateException("Stored risk evaluation is unreadable", failure); }
    }

    public Optional<StoredEvaluation> evaluationById(UUID evaluationId) {
        RiskEvaluationEntity e = entityManager.find(RiskEvaluationEntity.class, evaluationId);
        if (e == null) return Optional.empty();
        return Optional.of(new StoredEvaluation(e.id, e.tradePlanId, e.tradePlanVersion,
                e.accountId, e.status, e.decision, read(e.responsePayload, Response.class)));
    }

    public record StoredEvaluation(UUID id, UUID tradePlanId, long tradePlanVersion,
                                   UUID accountId, String status, String decision,
                                   Response response) { }
    public record AcknowledgmentDelivery(UUID evaluationId, UUID tradePlanId, long tradePlanVersion,
                                         String decision, Instant evaluatedAt, UUID claimToken) { }
    public record AccountConfiguration(UUID accountId, UUID brokerAccountId, String riskTimeZone,
                                       String reportingCurrency, UUID portfolioId) { }
    public record Baseline(long version, BigDecimal amount, String reportingCurrency,
                           Instant startsAt, Instant endsAt, int payloadSchemaVersion, String payload) { }
    public record Profile(UUID id, String semanticVersion, String policyId, String policyVersion,
                          String authority, Instant createdAt, String provenance,
                          Instant assignedAt, String assignmentProvenance, List<ProfileRule> rules) { }
    public record ProfileRule(String ruleId, String ruleVersion, String category, String severity,
                              int priority, BigDecimal maximumRatio, String provenance) { }

    public record ProfileKey(UUID id, String semanticVersion) implements Serializable { }
    public record ProfileRuleKey(UUID profileId, String profileSemanticVersion, String ruleId) implements Serializable { }
}

@Entity(name = "RiskProfileEntity") @Table(name = "risk_profile")
@IdClass(RiskPersistence.ProfileKey.class)
@Immutable
class RiskProfileEntity {
    @Id UUID id; @Id @Column(name="semantic_version", length=64) String semanticVersion;
    @Column(name="policy_id", nullable=false, length=160) String policyId;
    @Column(name="policy_version", nullable=false, length=64) String policyVersion;
    @Column(nullable=false, length=32) String authority;
    @Column(name="created_at", nullable=false) Instant createdAt;
    @Column(nullable=false, columnDefinition="text") String provenance;
}

@Entity(name = "RiskProfileRuleEntity") @Table(name = "risk_profile_rule")
@IdClass(RiskPersistence.ProfileRuleKey.class)
@Immutable
class RiskProfileRuleEntity {
    @Id @Column(name="profile_id") UUID profileId;
    @Id @Column(name="profile_semantic_version", length=64) String profileSemanticVersion;
    @Id @Column(name="rule_id", length=80) String ruleId;
    @Column(name="rule_version", nullable=false, length=64) String ruleVersion;
    @Column(nullable=false, length=32) String category;
    @Column(nullable=false, length=32) String severity;
    @Column(nullable=false) int priority;
    @Column(name="maximum_ratio", nullable=false, precision=30, scale=12) BigDecimal maximumRatio;
    @Column(nullable=false, columnDefinition="text") String provenance;
}

@Entity(name = "AccountRiskConfigurationEntity") @Table(name = "account_risk_configuration")
class AccountRiskConfigurationEntity {
    @Id @Column(name="account_id") UUID accountId;
    @Column(name="broker_account_id", nullable=false, unique=true) UUID brokerAccountId;
    @Column(name="risk_time_zone", nullable=false, length=80) String riskTimeZone;
    @Column(name="reporting_currency", nullable=false, length=16) String reportingCurrency;
    @Column(name="portfolio_id", nullable=false, unique=true) UUID portfolioId;
}

@Entity(name = "AccountRiskProfileAssignmentEntity") @Table(name = "account_risk_profile_assignment")
class AccountRiskProfileAssignmentEntity {
    @Id @Column(name="account_id") UUID accountId;
    @Column(name="profile_id", nullable=false) UUID profileId;
    @Column(name="profile_semantic_version", nullable=false, length=64) String profileSemanticVersion;
    @Column(name="assigned_at", nullable=false) Instant assignedAt;
    @Column(nullable=false, columnDefinition="text") String provenance;
}

@Entity(name = "RiskDayBaselineEntity") @Table(name = "risk_day_baseline")
@Immutable
class RiskDayBaselineEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) long version;
    @Column(name="account_id", nullable=false) UUID accountId; @Column(name="risk_day", nullable=false) LocalDate riskDay;
    @Column(name="starts_at", nullable=false) Instant startsAt; @Column(name="ends_at", nullable=false) Instant endsAt;
    @Column(name="reporting_currency", nullable=false, length=16) String reportingCurrency;
    @Column(nullable=false, precision=30, scale=12) BigDecimal amount;
    @Column(name="payload_schema_version", nullable=false) int payloadSchemaVersion;
    @Column(nullable=false, columnDefinition="text") String payload;
}

@Entity(name = "RiskComponentSnapshotEntity") @Table(name = "risk_component_snapshot")
@Immutable
class RiskComponentSnapshotEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) long version;
    @Column(name="evaluation_id", nullable=false) UUID evaluationId;
    @Column(name="component_type", nullable=false, length=32) String componentType;
    @Column(name="source_version", nullable=false, length=160) String sourceVersion;
    @Column(name="captured_at", nullable=false) Instant capturedAt;
    @Column(name="payload_schema_version", nullable=false) int payloadSchemaVersion;
    @Column(nullable=false, columnDefinition="text") String payload;
}

@Entity(name = "RiskContextSnapshotEntity") @Table(name = "risk_context_snapshot")
@Immutable
class RiskContextSnapshotEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) long version;
    @Column(name="evaluation_id", nullable=false, unique=true) UUID evaluationId;
    @Column(name="captured_at", nullable=false) Instant capturedAt;
    @Column(name="payload_schema_version", nullable=false) int payloadSchemaVersion;
    @Column(nullable=false, columnDefinition="text") String payload;
}

@Entity(name = "RiskEvaluationEntity") @Table(name = "risk_evaluation")
@Immutable
class RiskEvaluationEntity {
    @Id UUID id; @Column(name="actor_id", nullable=false) UUID actorId;
    @Column(name="idempotency_key", nullable=false, length=160) String idempotencyKey;
    @Column(name="trade_plan_id", nullable=false) UUID tradePlanId;
    @Column(name="trade_plan_version", nullable=false) long tradePlanVersion;
    @Column(name="account_id", nullable=false) UUID accountId;
    @Column(name="requested_at", nullable=false) Instant requestedAt;
    @Column(nullable=false, length=32) String status; @Column(length=32) String decision;
    @Column(name="context_snapshot_version") Long contextSnapshotVersion;
    @Column(name="result_schema_version", nullable=false) int resultSchemaVersion;
    @Column(name="result_payload", nullable=false, columnDefinition="text") String resultPayload;
    @Column(name="response_schema_version", nullable=false) int responseSchemaVersion;
    @Column(name="response_payload", nullable=false, columnDefinition="text") String responsePayload;
}

@Entity(name = "RiskAcknowledgmentOutboxEntity") @Table(name = "risk_acknowledgment_outbox")
class RiskAcknowledgmentOutboxEntity {
    @Id @Column(name="evaluation_id") UUID evaluationId;
    @Column(name="trade_plan_id", nullable=false) UUID tradePlanId;
    @Column(name="trade_plan_version", nullable=false) long tradePlanVersion;
    @Column(nullable=false, length=32) String decision;
    @Column(name="evaluated_at", nullable=false) Instant evaluatedAt;
    @Column(nullable=false, length=16) String status;
    @Column(name="attempt_count", nullable=false) int attemptCount;
    @Column(name="next_attempt_at") Instant nextAttemptAt;
    @Column(name="claim_token") UUID claimToken;
    @Column(name="lease_until") Instant leaseUntil;
    @Column(name="last_error", length=500) String lastError;
    @Column(name="created_at", nullable=false) Instant createdAt;
    @Column(name="updated_at", nullable=false) Instant updatedAt;
    @Column(name="delivered_at") Instant deliveredAt;
}
