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

    @Column(name = "risk_time_zone", length = 80, updatable = false)
    private String riskTimeZone;

    @Column(name = "risk_day_reset_time", length = 16, updatable = false)
    private java.time.LocalTime riskDayResetTime;

    @Column(name = "required_market_provider", length = 32, updatable = false)
    private String requiredMarketProvider;

    @Column(name = "required_provider_registration_reference", updatable = false)
    private UUID requiredProviderRegistrationReference;

    @Column(name = "active_risk_policy_id")
    private UUID activeRiskPolicyId;

    @Column(name = "active_risk_policy_version", length = 64)
    private String activeRiskPolicyVersion;

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

    @Column(name = "current_phase_key", length = 80)
    private String currentPhaseKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase_progression_state", length = 32)
    private ChallengePhaseProgressionState phaseProgressionState;

    @Column(name = "phase_progression_evidence", columnDefinition = "TEXT")
    private String phaseProgressionEvidence;

    @Column(name = "phase_progressed_at")
    private Instant phaseProgressedAt;

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
        this.riskTimeZone = definition.riskTimeZone();
        this.riskDayResetTime = definition.riskDayResetTime();
        this.requiredMarketProvider = definition.requiredMarketProvider();
        this.requiredProviderRegistrationReference = definition.requiredProviderRegistrationReference();
        ChallengePhaseDefinition first = definition.effectivePhases().get(0);
        this.activeRiskPolicyId = first.riskPolicyId();
        this.activeRiskPolicyVersion = first.riskPolicyVersion();
        this.startingCapital = positive(startingCapital, "startingCapital");
        this.capitalCurrency = definition.capitalCurrency();
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.status = ChallengeStatus.ACTIVE;
        this.activeMarker = ChallengeStatus.ACTIVE.name();
        if (!definition.phases().isEmpty()) {
            this.currentPhaseKey = first.phaseKey();
            this.phaseProgressionState = ChallengePhaseProgressionState.ACTIVE;
        }
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

    public void markLocallySatisfied(Instant at) {
        Objects.requireNonNull(at, "at");
        if (status != ChallengeStatus.ACTIVE) return;
        if (phaseProgressionState == null) return;
        if (phaseProgressionState == ChallengePhaseProgressionState.ACTIVE) {
            phaseProgressionState = ChallengePhaseProgressionState.LOCALLY_SATISFIED;
            phaseProgressedAt = at;
        }
    }

    public void confirmProviderProgression(Instant at, String evidenceReference) {
        Objects.requireNonNull(at, "at");
        if (status != ChallengeStatus.ACTIVE) return;
        if (phaseProgressionState != ChallengePhaseProgressionState.LOCALLY_SATISFIED) {
            throw new IllegalStateException("Phase must be locally satisfied before provider confirmation");
        }
        if (evidenceReference == null || evidenceReference.isBlank()) {
            throw new IllegalArgumentException("provider progression evidence is required");
        }
        phaseProgressionState = ChallengePhaseProgressionState.PROVIDER_CONFIRMED;
        phaseProgressionEvidence = evidenceReference.strip();
        phaseProgressedAt = at;
    }

    public void advanceTo(ChallengePhaseDefinition next, Instant at) {
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(at, "at");
        if (status != ChallengeStatus.ACTIVE) throw new IllegalStateException("Challenge is not active");
        if (phaseProgressionState != ChallengePhaseProgressionState.LOCALLY_SATISFIED
                && phaseProgressionState != ChallengePhaseProgressionState.PROVIDER_CONFIRMED) {
            throw new IllegalStateException("Current phase is not satisfied");
        }
        currentPhaseKey = next.phaseKey();
        phaseProgressionState = ChallengePhaseProgressionState.ACTIVE;
        phaseProgressionEvidence = null;
        phaseProgressedAt = at;
        activeRiskPolicyId = next.riskPolicyId();
        activeRiskPolicyVersion = next.riskPolicyVersion();
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
    public String riskTimeZone() { return riskTimeZone; }
    public java.time.LocalTime riskDayResetTime() { return riskDayResetTime; }
    public String requiredMarketProvider() { return requiredMarketProvider; }
    public UUID requiredProviderRegistrationReference() { return requiredProviderRegistrationReference; }
    public UUID riskPolicyId() { return riskPolicyId; }
    public String riskPolicyVersion() { return riskPolicyVersion; }
    public UUID activeRiskPolicyId() { return activeRiskPolicyId == null ? riskPolicyId : activeRiskPolicyId; }
    public String activeRiskPolicyVersion() {
        return activeRiskPolicyVersion == null ? riskPolicyVersion : activeRiskPolicyVersion;
    }
    public BigDecimal startingCapital() { return startingCapital; }
    public String capitalCurrency() { return capitalCurrency; }
    public Instant startedAt() { return startedAt; }
    public ChallengeStatus status() { return status; }
    public Instant passedAt() { return passedAt; }
    public Instant breachedAt() { return breachedAt; }
    public String terminalReason() { return terminalReason; }
    public UUID terminalRiskEvaluationId() { return terminalRiskEvaluationId; }
    public String currentPhaseKey() { return currentPhaseKey; }
    public ChallengePhaseProgressionState phaseProgressionState() { return phaseProgressionState; }
    public String phaseProgressionEvidence() { return phaseProgressionEvidence; }
    public Instant phaseProgressedAt() { return phaseProgressedAt; }
    public long version() { return version; }
}
