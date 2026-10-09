package com.hope.trading.trading_core.challenge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "challenge_definition")
@IdClass(ChallengeDefinitionKey.class)
public class ChallengeDefinition {
    private static final int MAX_IDENTIFIER_LENGTH = 80;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Id
    @Column(name = "definition_version", nullable = false, length = 64, updatable = false)
    private String definitionVersion;

    @Column(nullable = false, length = MAX_IDENTIFIER_LENGTH, updatable = false)
    private String provider;

    @Column(nullable = false, length = MAX_IDENTIFIER_LENGTH, updatable = false)
    private String product;

    @Column(name = "plan_code", nullable = false, length = MAX_IDENTIFIER_LENGTH, updatable = false)
    private String planCode;

    @Column(name = "capital_currency", nullable = false, length = 16, updatable = false)
    private String capitalCurrency;

    @Column(name = "starting_capital", nullable = false, precision = 30, scale = 12, updatable = false)
    private BigDecimal startingCapital;

    @Column(name = "profit_target_ratio", nullable = false, precision = 30, scale = 12, updatable = false)
    private BigDecimal profitTargetRatio;

    @Enumerated(EnumType.STRING)
    @Column(name = "progression_value_source", nullable = false, length = 32, updatable = false)
    private ProgressionValueSource progressionValueSource;

    @Column(name = "risk_policy_id", nullable = false, updatable = false)
    private UUID riskPolicyId;

    @Column(name = "risk_policy_version", nullable = false, length = 64, updatable = false)
    private String riskPolicyVersion;

    @Column(name = "source_url", nullable = false, length = 2048, updatable = false)
    private String sourceUrl;

    @Column(name = "retrieved_at", nullable = false, updatable = false)
    private Instant retrievedAt;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private Instant effectiveFrom;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String provenance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChallengeDefinition() {
    }

    private ChallengeDefinition(UUID id, String definitionVersion, String provider, String product,
                                String planCode, String capitalCurrency, BigDecimal startingCapital,
                                BigDecimal profitTargetRatio, ProgressionValueSource progressionValueSource,
                                UUID riskPolicyId, String riskPolicyVersion, String sourceUrl,
                                Instant retrievedAt, Instant effectiveFrom, String provenance, Instant createdAt) {
        this.id = requireId(id, "id");
        this.definitionVersion = required(definitionVersion, "definitionVersion", 64);
        this.provider = required(provider, "provider", MAX_IDENTIFIER_LENGTH);
        this.product = required(product, "product", MAX_IDENTIFIER_LENGTH);
        this.planCode = required(planCode, "planCode", MAX_IDENTIFIER_LENGTH);
        this.capitalCurrency = required(capitalCurrency, "capitalCurrency", 16).toUpperCase();
        this.startingCapital = positive(startingCapital, "startingCapital");
        this.profitTargetRatio = positive(profitTargetRatio, "profitTargetRatio");
        this.progressionValueSource = Objects.requireNonNull(progressionValueSource, "progressionValueSource");
        this.riskPolicyId = requireId(riskPolicyId, "riskPolicyId");
        this.riskPolicyVersion = required(riskPolicyVersion, "riskPolicyVersion", 64);
        this.sourceUrl = required(sourceUrl, "sourceUrl", 2048);
        this.retrievedAt = Objects.requireNonNull(retrievedAt, "retrievedAt");
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        this.provenance = required(provenance, "provenance", Integer.MAX_VALUE);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static ChallengeDefinition create(UUID id, String definitionVersion, String provider, String product,
                                             String planCode, String capitalCurrency, BigDecimal startingCapital,
                                             BigDecimal profitTargetRatio, ProgressionValueSource progressionValueSource,
                                             UUID riskPolicyId, String riskPolicyVersion, String sourceUrl,
                                             Instant retrievedAt, Instant effectiveFrom, String provenance,
                                             Instant createdAt) {
        return new ChallengeDefinition(id, definitionVersion, provider, product, planCode, capitalCurrency,
                startingCapital, profitTargetRatio, progressionValueSource, riskPolicyId, riskPolicyVersion,
                sourceUrl, retrievedAt, effectiveFrom, provenance, createdAt);
    }

    @PreUpdate
    private void rejectUpdate() {
        throw new IllegalStateException("Challenge definitions are immutable");
    }

    private static UUID requireId(UUID value, String name) {
        return Objects.requireNonNull(value, name);
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.strip().length() > maxLength) {
            throw new IllegalArgumentException(name + " is required and must not exceed " + maxLength + " characters");
        }
        return value.strip();
    }

    private static BigDecimal positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public UUID id() { return id; }
    public String definitionVersion() { return definitionVersion; }
    public String provider() { return provider; }
    public String product() { return product; }
    public String planCode() { return planCode; }
    public String capitalCurrency() { return capitalCurrency; }
    public BigDecimal startingCapital() { return startingCapital; }
    public BigDecimal profitTargetRatio() { return profitTargetRatio; }
    public ProgressionValueSource progressionValueSource() { return progressionValueSource; }
    public UUID riskPolicyId() { return riskPolicyId; }
    public String riskPolicyVersion() { return riskPolicyVersion; }
    public String sourceUrl() { return sourceUrl; }
    public Instant retrievedAt() { return retrievedAt; }
    public Instant effectiveFrom() { return effectiveFrom; }
    public String provenance() { return provenance; }
    public Instant createdAt() { return createdAt; }
}
