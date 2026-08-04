package com.hope.trading.trading_core.tradeplanning.infrastructure;

import com.hope.trading.trading_core.tradeplanning.application.TradePlanningProfileRepository;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile.PlanningPreferences;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile.RiskBudget;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.springframework.stereotype.Repository;

@Repository
public class TradePlanningProfileJpaRepository implements TradePlanningProfileRepository {
    private final EntityManager entityManager;
    public TradePlanningProfileJpaRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    @Override
    public Optional<TradePlanningProfile> find(UUID id, long version) {
        return Optional.ofNullable(entityManager.find(TradePlanningProfileEntity.class, new ProfileKey(id, version)))
                .map(this::domain);
    }
    @Override
    public Optional<TradePlanningProfile> findLatest(UUID id) {
        return entityManager.createQuery("select p from TradePlanningProfileEntity p where p.profileId=:id order by p.profileVersion desc",
                        TradePlanningProfileEntity.class).setParameter("id", id).setMaxResults(1)
                .getResultStream().findFirst().map(this::domain);
    }
    @Override
    public Optional<TradePlanningProfile> findAssigned(UUID accountId) {
        return entityManager.createQuery(
                        "select a from AccountTradePlanningProfileAssignmentEntity a "
                                + "where a.accountId=:accountId order by a.assignmentVersion desc",
                        AccountTradePlanningProfileAssignmentEntity.class)
                .setParameter("accountId", accountId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .flatMap(assignment -> find(assignment.profileId, assignment.profileVersion));
    }
    @Override
    public TradePlanningProfile append(TradePlanningProfile profile) {
        entityManager.persist(entity(profile));
        entityManager.flush();
        return profile;
    }
    @Override
    public void assign(UUID accountId, UUID profileId, long profileVersion, UUID actorId, Instant assignedAt) {
        long assignmentVersion = entityManager.createQuery(
                        "select coalesce(max(a.assignmentVersion), 0) "
                                + "from AccountTradePlanningProfileAssignmentEntity a where a.accountId=:accountId",
                        Long.class)
                .setParameter("accountId", accountId)
                .getSingleResult() + 1;
        AccountTradePlanningProfileAssignmentEntity value = new AccountTradePlanningProfileAssignmentEntity();
        value.accountId = accountId;
        value.assignmentVersion = assignmentVersion;
        value.profileId = profileId;
        value.profileVersion = profileVersion;
        value.assignedBy = actorId;
        value.assignedAt = assignedAt;
        entityManager.persist(value);
        entityManager.flush();
    }

    private TradePlanningProfile domain(TradePlanningProfileEntity value) {
        return new TradePlanningProfile(value.profileId, value.profileVersion, value.ownerId,
                new RiskBudget(value.riskBudgetAmount, value.currency, value.profileId, value.profileVersion),
                new PlanningPreferences(value.profileId, value.profileVersion,
                        TradePlanningProfile.EntryType.valueOf(value.entryType),
                        TradePlanningProfile.StopStrategy.valueOf(value.stopStrategy), value.stopDistancePercent,
                        TradePlanningProfile.TargetStrategy.valueOf(value.targetStrategy), value.targetRiskMultiple,
                        TradePlanningProfile.PlanningHorizon.valueOf(value.planningHorizon),
                        Duration.ofSeconds(value.validitySeconds)), value.createdAt);
    }
    private TradePlanningProfileEntity entity(TradePlanningProfile value) {
        TradePlanningProfileEntity result = new TradePlanningProfileEntity();
        result.profileId = value.id(); result.profileVersion = value.version(); result.ownerId = value.ownerId();
        result.riskBudgetAmount = value.riskBudget().amount(); result.currency = value.riskBudget().currency();
        result.entryType = value.preferences().entryType().name();
        result.stopStrategy = value.preferences().stopStrategy().name();
        result.stopDistancePercent = value.preferences().stopDistancePercent();
        result.targetStrategy = value.preferences().targetStrategy().name();
        result.targetRiskMultiple = value.preferences().targetRiskMultiple();
        result.planningHorizon = value.preferences().horizon().name();
        result.validitySeconds = value.preferences().validity().toSeconds(); result.createdAt = value.createdAt();
        return result;
    }
}

record ProfileKey(UUID profileId, long profileVersion) implements Serializable { }

@Entity
@Immutable
@IdClass(ProfileKey.class)
@Table(name = "trade_planning_profiles")
class TradePlanningProfileEntity {
    @Id @Column(name = "profile_id") UUID profileId;
    @Id @Column(name = "profile_version") long profileVersion;
    @Column(name = "owner_id", nullable = false) UUID ownerId;
    @Column(name = "risk_budget_amount", nullable = false, precision = 30, scale = 12) java.math.BigDecimal riskBudgetAmount;
    @Column(nullable = false, length = 16) String currency;
    @Column(name = "entry_type", nullable = false, length = 32) String entryType;
    @Column(name = "stop_strategy", nullable = false, length = 64) String stopStrategy;
    @Column(name = "stop_distance_percent", nullable = false, precision = 18, scale = 8) java.math.BigDecimal stopDistancePercent;
    @Column(name = "target_strategy", nullable = false, length = 64) String targetStrategy;
    @Column(name = "target_risk_multiple", nullable = false, precision = 18, scale = 8) java.math.BigDecimal targetRiskMultiple;
    @Column(name = "planning_horizon", nullable = false, length = 32) String planningHorizon;
    @Column(name = "validity_seconds", nullable = false) long validitySeconds;
    @Column(name = "created_at", nullable = false) Instant createdAt;
}

@Entity
@Immutable
@IdClass(AssignmentKey.class)
@Table(name = "account_trade_planning_profile_assignments")
class AccountTradePlanningProfileAssignmentEntity {
    @Id @Column(name = "account_id") UUID accountId;
    @Id @Column(name = "assignment_version") long assignmentVersion;
    @Column(name = "profile_id", nullable = false) UUID profileId;
    @Column(name = "profile_version", nullable = false) long profileVersion;
    @Column(name = "assigned_by", nullable = false) UUID assignedBy;
    @Column(name = "assigned_at", nullable = false) Instant assignedAt;
}

record AssignmentKey(UUID accountId, long assignmentVersion) implements Serializable { }
