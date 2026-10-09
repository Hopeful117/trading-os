package com.hope.trading.trading_core.challenge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "challenge_instance")
public class ChallengeInstance {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "definition_id", nullable = false, updatable = false)
    private UUID definitionId;

    @Column(name = "definition_version", nullable = false, length = 64, updatable = false)
    private String definitionVersion;

    @Column(name = "risk_policy_id", nullable = false, updatable = false)
    private UUID riskPolicyId;

    @Column(name = "risk_policy_version", nullable = false, length = 64, updatable = false)
    private String riskPolicyVersion;

    @Column(name = "starting_capital", nullable = false, precision = 30, scale = 12, updatable = false)
    private BigDecimal startingCapital;

    @Column(name = "capital_currency", nullable = false, length = 16, updatable = false)
    private String capitalCurrency;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChallengeStatus status;

    @Column(name = "active_marker", length = 16)
    private String activeMarker;

    @Column(name = "passed_at")
    private Instant passedAt;

    @Column(name = "breached_at")
    private Instant breachedAt;

    @Column(name = "terminal_reason", length = 255)
    private String terminalReason;

    @Column(name = "terminal_risk_evaluation_id")
    private UUID terminalRiskEvaluationId;

    @Version
    @Column(nullable = false)
    private long version;

    protected ChallengeInstance() {
    }

    private ChallengeInstance(UUID id, UUID accountId, ChallengeDefinition definition, BigDecimal startingCapital,
                              Instant startedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.definitionId = definition.id();
        this.definitionVersion = definition.definitionVersion();
        this.riskPolicyId = definition.riskPolicyId();
        this.riskPolicyVersion = definition.riskPolicyVersion();
        this.startingCapital = positive(startingCapital, "startingCapital");
        this.capitalCurrency = definition.capitalCurrency();
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.status = ChallengeStatus.ACTIVE;
        this.activeMarker = ChallengeStatus.ACTIVE.name();
    }

    public static ChallengeInstance start(UUID id, UUID accountId, ChallengeDefinition definition,
                                          BigDecimal startingCapital, Instant startedAt) {
        return new ChallengeInstance(id, accountId, Objects.requireNonNull(definition, "definition"),
                startingCapital, startedAt);
    }

    public void pass(Instant at, String reason) {
        pass(at, reason, null);
    }

    public void pass(Instant at, String reason, UUID riskEvaluationId) {
        transition(ChallengeStatus.PASSED, at, reason, riskEvaluationId);
    }

    public void breach(Instant at, String reason) {
        breach(at, reason, null);
    }

    public void breach(Instant at, String reason, UUID riskEvaluationId) {
        transition(ChallengeStatus.BREACHED, at, reason, riskEvaluationId);
    }

    private void transition(ChallengeStatus target, Instant at, String reason, UUID riskEvaluationId) {
        if (status == target) return;
        Objects.requireNonNull(at, "at");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("terminal reason is required");
        if (status != ChallengeStatus.ACTIVE) {
            throw new IllegalStateException("Challenge is already terminal");
        }
        status = target;
        activeMarker = null;
        terminalReason = reason.strip();
        terminalRiskEvaluationId = riskEvaluationId;
        if (target == ChallengeStatus.PASSED) passedAt = at;
        if (target == ChallengeStatus.BREACHED) breachedAt = at;
    }

    @PrePersist
    private void validatePersistenceState() {
        if (status == ChallengeStatus.ACTIVE && activeMarker == null) {
            activeMarker = ChallengeStatus.ACTIVE.name();
        }
    }

    private static BigDecimal positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    public UUID id() { return id; }
    public UUID accountId() { return accountId; }
    public UUID definitionId() { return definitionId; }
    public String definitionVersion() { return definitionVersion; }
    public UUID riskPolicyId() { return riskPolicyId; }
    public String riskPolicyVersion() { return riskPolicyVersion; }
    public BigDecimal startingCapital() { return startingCapital; }
    public String capitalCurrency() { return capitalCurrency; }
    public Instant startedAt() { return startedAt; }
    public ChallengeStatus status() { return status; }
    public Instant passedAt() { return passedAt; }
    public Instant breachedAt() { return breachedAt; }
    public String terminalReason() { return terminalReason; }
    public UUID terminalRiskEvaluationId() { return terminalRiskEvaluationId; }
    public long version() { return version; }
}
