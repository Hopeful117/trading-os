package com.hope.trading.market_intelligence.infrastructure.research;

import com.hope.trading.market_intelligence.domain.research.HistoricalDatasetExportJobStatus;
import com.hope.trading.market_intelligence.domain.research.ExportContractValidationStatus;
import com.hope.trading.market_intelligence.domain.research.ExperimentEligibilityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "historical_dataset_export_job")
public class HistoricalDatasetExportJobEntity {
    @Id private UUID id;
    @Column(name = "owner_user_id", nullable = false, updatable = false) private UUID ownerUserId;
    @Column(name = "agent_id", nullable = false, updatable = false) private UUID agentId;
    @Column(name = "scope_id", nullable = false, updatable = false) private UUID scopeId;
    @Column(name = "grant_id", nullable = false, updatable = false) private UUID grantId;
    @Column(name = "grant_version", nullable = false, updatable = false) private long grantVersion;
    @Column(name = "credential_id", nullable = false, updatable = false) private UUID credentialId;
    @Column(name = "credential_version", nullable = false, updatable = false) private int credentialVersion;
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 200) private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 128) private String requestFingerprint;
    @Column(name = "request_json", nullable = false, updatable = false, columnDefinition = "text") private String requestJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private HistoricalDatasetExportJobStatus status;
    @Column(name = "artifact_id", length = 128) private String artifactId;
    @Column(name = "artifact_checksum", length = 128) private String artifactChecksum;
    @Column(name = "artifact_bytes") private Long artifactBytes;
    @Column(name = "sample_count") private Integer sampleCount;
    @Column(name = "failure_code", length = 64) private String failureCode;
    @Enumerated(EnumType.STRING) @Column(name = "contract_validation_status", nullable = false, length = 16)
    private ExportContractValidationStatus contractValidationStatus;
    @Enumerated(EnumType.STRING) @Column(name = "experiment_eligibility_status", nullable = false, length = 16)
    private ExperimentEligibilityStatus experimentEligibilityStatus;
    @Column(name = "correlation_id", length = 128, updatable = false) private String correlationId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Version private long version;

    protected HistoricalDatasetExportJobEntity() { }

    public static HistoricalDatasetExportJobEntity create(UUID ownerUserId, UUID agentId, UUID scopeId,
                                                           UUID grantId, long grantVersion, UUID credentialId,
                                                           int credentialVersion, String idempotencyKey,
                                                           String requestFingerprint, String requestJson,
                                                           String correlationId, Instant now, Instant expiresAt) {
        var value = new HistoricalDatasetExportJobEntity();
        value.id = UUID.randomUUID(); value.ownerUserId = ownerUserId; value.agentId = agentId;
        value.scopeId = scopeId; value.grantId = grantId; value.grantVersion = grantVersion;
        value.credentialId = credentialId; value.credentialVersion = credentialVersion;
        value.idempotencyKey = idempotencyKey; value.requestFingerprint = requestFingerprint;
        value.requestJson = requestJson; value.correlationId = correlationId;
        value.status = HistoricalDatasetExportJobStatus.PENDING; value.createdAt = now;
        value.contractValidationStatus = ExportContractValidationStatus.PENDING;
        value.experimentEligibilityStatus = ExperimentEligibilityStatus.NOT_EVALUATED;
        value.expiresAt = expiresAt; return value;
    }

    public void running(Instant now) { status = HistoricalDatasetExportJobStatus.RUNNING; startedAt = now; }
    public void success(String artifactId, String checksum, long bytes, int samples, Instant now) {
        status = HistoricalDatasetExportJobStatus.SUCCEEDED; this.artifactId = artifactId;
        artifactChecksum = checksum; artifactBytes = bytes; sampleCount = samples; completedAt = now;
        contractValidationStatus = ExportContractValidationStatus.VALID;
    }
    public void fail(String code, Instant now) {
        status = HistoricalDatasetExportJobStatus.FAILED;
        contractValidationStatus = ExportContractValidationStatus.INVALID;
        failureCode = code;
        completedAt = now;
    }
    public void expire(Instant now) { status = HistoricalDatasetExportJobStatus.EXPIRED; failureCode = "AUTHORIZATION_EXPIRED"; completedAt = now; }
    public UUID id() { return id; } public UUID ownerUserId() { return ownerUserId; } public UUID agentId() { return agentId; }
    public UUID scopeId() { return scopeId; } public UUID grantId() { return grantId; } public long grantVersion() { return grantVersion; }
    public UUID credentialId() { return credentialId; } public int credentialVersion() { return credentialVersion; }
    public String idempotencyKey() { return idempotencyKey; } public String requestFingerprint() { return requestFingerprint; }
    public String requestJson() { return requestJson; } public HistoricalDatasetExportJobStatus status() { return status; }
    public String artifactId() { return artifactId; } public String artifactChecksum() { return artifactChecksum; }
    public Long artifactBytes() { return artifactBytes; } public Integer sampleCount() { return sampleCount; }
    public String failureCode() { return failureCode; } public String correlationId() { return correlationId; }
    public ExportContractValidationStatus contractValidationStatus() { return contractValidationStatus; }
    public ExperimentEligibilityStatus experimentEligibilityStatus() { return experimentEligibilityStatus; }
    public Instant createdAt() { return createdAt; } public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; } public Instant expiresAt() { return expiresAt; }
}
