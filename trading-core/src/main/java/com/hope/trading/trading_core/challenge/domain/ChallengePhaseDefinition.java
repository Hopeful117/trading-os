package com.hope.trading.trading_core.challenge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Immutable, provider-neutral configuration for one ordered Challenge phase. */
@Embeddable
public class ChallengePhaseDefinition {
    @Column(name = "phase_key", nullable = false, length = 80, updatable = false)
    private String phaseKey;

    @Column(name = "phase_order", nullable = false, updatable = false)
    private int phaseOrder;

    @Column(name = "profit_target_ratio", precision = 30, scale = 12, updatable = false)
    private BigDecimal profitTargetRatio;

    @Enumerated(EnumType.STRING)
    @Column(name = "progression_value_source", length = 32, updatable = false)
    private ProgressionValueSource progressionValueSource;

    @Column(name = "minimum_trading_days", updatable = false)
    private Integer minimumTradingDays;

    @Column(name = "risk_policy_id", updatable = false)
    private UUID riskPolicyId;

    @Column(name = "risk_policy_version", length = 64, updatable = false)
    private String riskPolicyVersion;

    @Column(name = "provider_confirmation_required", nullable = false, updatable = false)
    private boolean providerConfirmationRequired;

    protected ChallengePhaseDefinition() {
    }

    private ChallengePhaseDefinition(String phaseKey, int phaseOrder, BigDecimal profitTargetRatio,
                                     ProgressionValueSource progressionValueSource,
                                     Integer minimumTradingDays, UUID riskPolicyId,
                                     String riskPolicyVersion, boolean providerConfirmationRequired) {
        if (phaseKey == null || phaseKey.isBlank() || phaseKey.strip().length() > 80) {
            throw new IllegalArgumentException("phaseKey is required and must not exceed 80 characters");
        }
        if (phaseOrder < 1) throw new IllegalArgumentException("phaseOrder must be positive");
        if (profitTargetRatio != null && profitTargetRatio.signum() <= 0) {
            throw new IllegalArgumentException("profitTargetRatio must be positive");
        }
        if (profitTargetRatio != null && progressionValueSource == null) {
            throw new IllegalArgumentException("progressionValueSource is required for a profit target");
        }
        if (minimumTradingDays != null && minimumTradingDays < 1) {
            throw new IllegalArgumentException("minimumTradingDays must be positive");
        }
        if ((riskPolicyId == null) != (riskPolicyVersion == null)) {
            throw new IllegalArgumentException("risk policy id and version must be provided together");
        }
        this.phaseKey = phaseKey.strip();
        this.phaseOrder = phaseOrder;
        this.profitTargetRatio = profitTargetRatio;
        this.progressionValueSource = progressionValueSource;
        this.minimumTradingDays = minimumTradingDays;
        this.riskPolicyId = riskPolicyId;
        this.riskPolicyVersion = riskPolicyVersion;
        this.providerConfirmationRequired = providerConfirmationRequired;
    }

    public static ChallengePhaseDefinition create(String phaseKey, int phaseOrder,
                                                   BigDecimal profitTargetRatio,
                                                   ProgressionValueSource progressionValueSource,
                                                   Integer minimumTradingDays, UUID riskPolicyId,
                                                   String riskPolicyVersion,
                                                   boolean providerConfirmationRequired) {
        return new ChallengePhaseDefinition(phaseKey, phaseOrder, profitTargetRatio,
                progressionValueSource, minimumTradingDays, riskPolicyId, riskPolicyVersion,
                providerConfirmationRequired);
    }

    ChallengePhaseDefinition withRiskPolicy(UUID id, String version) {
        if (riskPolicyId != null) return this;
        return new ChallengePhaseDefinition(phaseKey, phaseOrder, profitTargetRatio,
                progressionValueSource, minimumTradingDays, Objects.requireNonNull(id),
                Objects.requireNonNull(version), providerConfirmationRequired);
    }

    public String phaseKey() { return phaseKey; }
    public int phaseOrder() { return phaseOrder; }
    public BigDecimal profitTargetRatio() { return profitTargetRatio; }
    public ProgressionValueSource progressionValueSource() { return progressionValueSource; }
    public Integer minimumTradingDays() { return minimumTradingDays; }
    public UUID riskPolicyId() { return riskPolicyId; }
    public String riskPolicyVersion() { return riskPolicyVersion; }
    public boolean providerConfirmationRequired() { return providerConfirmationRequired; }
}
