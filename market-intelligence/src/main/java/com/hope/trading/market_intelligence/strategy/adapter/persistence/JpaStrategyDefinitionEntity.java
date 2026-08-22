package com.hope.trading.market_intelligence.strategy.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "strategy_definitions")
@IdClass(JpaStrategyDefinitionEntity.Pk.class)
public class JpaStrategyDefinitionEntity {

    @Id
    @Column(name = "strategy_id", nullable = false)
    private UUID strategyId;

    @Id
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "operational_status", nullable = false, length = 30)
    private String operationalStatus;

    @Column(name = "validation_status", nullable = false, length = 20)
    private String validationStatus;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "asset_classes", nullable = false, columnDefinition = "text")
    private String assetClasses;

    @Column(name = "timeframes", nullable = false, columnDefinition = "text")
    private String timeframes;

    @Column(name = "providers", columnDefinition = "text")
    private String providers;

    @Column(name = "required_inputs", nullable = false, columnDefinition = "text")
    private String requiredInputs;

    @Column(name = "parameters", nullable = false, columnDefinition = "text")
    private String parameters;

    @Column(name = "research_ref", length = 200)
    private String researchRef;

    @Column(name = "validation_evidence_ref", length = 200)
    private String validationEvidenceRef;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public static class Pk implements Serializable {
        private UUID strategyId;
        private int version;

        public Pk() {
        }

        public Pk(UUID strategyId, int version) {
            this.strategyId = strategyId;
            this.version = version;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Pk pk
                    && version == pk.version
                    && strategyId.equals(pk.strategyId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(strategyId, version);
        }
    }

    public UUID getStrategyId() { return strategyId; }

    public void setStrategyId(UUID strategyId) { this.strategyId = strategyId; }

    public int getVersion() { return version; }

    public void setVersion(int version) { this.version = version; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public String getOperationalStatus() { return operationalStatus; }

    public void setOperationalStatus(String operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public String getValidationStatus() { return validationStatus; }

    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }

    public String getDirection() { return direction; }

    public void setDirection(String direction) { this.direction = direction; }

    public String getAssetClasses() { return assetClasses; }

    public void setAssetClasses(String assetClasses) { this.assetClasses = assetClasses; }

    public String getTimeframes() { return timeframes; }

    public void setTimeframes(String timeframes) { this.timeframes = timeframes; }

    public String getProviders() { return providers; }

    public void setProviders(String providers) { this.providers = providers; }

    public String getRequiredInputs() { return requiredInputs; }

    public void setRequiredInputs(String requiredInputs) { this.requiredInputs = requiredInputs; }

    public String getParameters() { return parameters; }

    public void setParameters(String parameters) { this.parameters = parameters; }

    public String getResearchRef() { return researchRef; }

    public void setResearchRef(String researchRef) { this.researchRef = researchRef; }

    public String getValidationEvidenceRef() { return validationEvidenceRef; }

    public void setValidationEvidenceRef(String validationEvidenceRef) {
        this.validationEvidenceRef = validationEvidenceRef;
    }

    public Instant getCreatedAt() { return createdAt; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
