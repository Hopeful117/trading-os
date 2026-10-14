package com.hope.trading.trading_core.challenge.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ChallengeReevaluationOutboxPersistence {
    private static final String PENDING = "PENDING";
    private static final String PROCESSING = "PROCESSING";
    private static final String DELIVERED = "DELIVERED";
    private static final String FAILED = "FAILED";
    private static final long LEASE_SECONDS = 300;

    private final EntityManager entityManager;

    public ChallengeReevaluationOutboxPersistence(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void enqueue(UUID executionIntentId, UUID accountId, Instant now) {
        if (entityManager.find(ChallengeReevaluationOutboxEntity.class, executionIntentId) != null) return;
        ChallengeReevaluationOutboxEntity entity = new ChallengeReevaluationOutboxEntity();
        entity.executionIntentId = executionIntentId;
        entity.accountId = accountId;
        entity.status = PENDING;
        entity.attemptCount = 0;
        entity.nextAttemptAt = now;
        entity.createdAt = now;
        entity.updatedAt = now;
        entityManager.persist(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Delivery> claim(UUID executionIntentId, Instant now, boolean ignoreSchedule) {
        ChallengeReevaluationOutboxEntity entity = entityManager.find(
                ChallengeReevaluationOutboxEntity.class, executionIntentId, LockModeType.PESSIMISTIC_WRITE);
        if (entity == null || !claimable(entity, now, ignoreSchedule)) return Optional.empty();

        UUID token = UUID.randomUUID();
        entity.status = PROCESSING;
        entity.claimToken = token;
        entity.leaseUntil = now.plus(LEASE_SECONDS, ChronoUnit.SECONDS);
        entity.attemptCount++;
        entity.updatedAt = now;
        entityManager.flush();
        return Optional.of(new Delivery(entity.executionIntentId, entity.accountId, token));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void renew(UUID executionIntentId, UUID claimToken, Instant now) {
        ChallengeReevaluationOutboxEntity entity = claimed(executionIntentId, claimToken);
        if (entity == null) return;
        entity.leaseUntil = now.plus(LEASE_SECONDS, ChronoUnit.SECONDS);
        entity.updatedAt = now;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delivered(UUID executionIntentId, UUID claimToken, Instant now) {
        ChallengeReevaluationOutboxEntity entity = claimed(executionIntentId, claimToken);
        if (entity == null) return;
        entity.status = DELIVERED;
        entity.nextAttemptAt = null;
        entity.claimToken = null;
        entity.leaseUntil = null;
        entity.lastError = null;
        entity.deliveredAt = now;
        entity.updatedAt = now;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(UUID executionIntentId, UUID claimToken, Instant now, String error, int maxAttempts) {
        ChallengeReevaluationOutboxEntity entity = claimed(executionIntentId, claimToken);
        if (entity == null) return;
        if (entity.attemptCount >= maxAttempts) {
            entity.status = FAILED;
            entity.nextAttemptAt = null;
            entity.claimToken = null;
            entity.leaseUntil = null;
            entity.lastError = error == null
                    ? "Challenge reevaluation failed after maximum attempts"
                    : error.substring(0, Math.min(500, error.length()));
            entity.updatedAt = now;
            return;
        }
        long delaySeconds = Math.min(300, 1L << Math.min(entity.attemptCount, 8));
        entity.status = PENDING;
        entity.nextAttemptAt = now.plusSeconds(delaySeconds);
        entity.claimToken = null;
        entity.leaseUntil = null;
        entity.lastError = error == null
                ? "Challenge reevaluation failed"
                : error.substring(0, Math.min(500, error.length()));
        entity.updatedAt = now;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> due(Instant now, int limit) {
        return entityManager.createQuery("select o.executionIntentId from ChallengeReevaluationOutboxEntity o "
                        + "where (o.status='PENDING' and o.nextAttemptAt<=:now) "
                        + "or (o.status='PROCESSING' and o.leaseUntil<=:now) "
                        + "order by coalesce(o.nextAttemptAt, o.leaseUntil)", UUID.class)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteDeliveredBefore(Instant cutoff, int limit) {
        List<UUID> ids = entityManager.createQuery("select o.executionIntentId from "
                        + "ChallengeReevaluationOutboxEntity o where o.status='DELIVERED' "
                        + "and o.deliveredAt < :cutoff order by o.deliveredAt", UUID.class)
                .setParameter("cutoff", cutoff)
                .setMaxResults(limit)
                .getResultList();
        ids.forEach(id -> entityManager.remove(entityManager.find(ChallengeReevaluationOutboxEntity.class, id)));
        return ids.size();
    }

    private boolean claimable(ChallengeReevaluationOutboxEntity entity, Instant now, boolean ignoreSchedule) {
        if (DELIVERED.equals(entity.status) || FAILED.equals(entity.status)) return false;
        boolean expired = PROCESSING.equals(entity.status)
                && entity.leaseUntil != null && !entity.leaseUntil.isAfter(now);
        boolean due = entity.nextAttemptAt == null || !entity.nextAttemptAt.isAfter(now);
        if (PROCESSING.equals(entity.status) && !expired) return false;
        return ignoreSchedule || due;
    }

    private ChallengeReevaluationOutboxEntity claimed(UUID executionIntentId, UUID claimToken) {
        ChallengeReevaluationOutboxEntity entity = entityManager.find(
                ChallengeReevaluationOutboxEntity.class, executionIntentId, LockModeType.PESSIMISTIC_WRITE);
        return entity != null && PROCESSING.equals(entity.status) && claimToken.equals(entity.claimToken)
                ? entity : null;
    }

    public record Delivery(UUID executionIntentId, UUID accountId, UUID claimToken) {}
}

@Entity(name = "ChallengeReevaluationOutboxEntity")
@Table(name = "challenge_reevaluation_outbox")
class ChallengeReevaluationOutboxEntity {
    @Id
    @Column(name = "execution_intent_id")
    UUID executionIntentId;

    @Column(name = "account_id", nullable = false)
    UUID accountId;

    @Column(nullable = false, length = 16)
    String status;

    @Column(name = "attempt_count", nullable = false)
    int attemptCount;

    @Column(name = "next_attempt_at")
    Instant nextAttemptAt;

    @Column(name = "claim_token")
    UUID claimToken;

    @Column(name = "lease_until")
    Instant leaseUntil;

    @Column(name = "last_error", length = 500)
    String lastError;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Column(name = "delivered_at")
    Instant deliveredAt;
}
