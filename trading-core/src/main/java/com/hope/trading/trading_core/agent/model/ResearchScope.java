package com.hope.trading.trading_core.agent.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.FetchType;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "research_scope")
public class ResearchScope {
    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    @ElementCollection
    @CollectionTable(name = "research_scope_market", joinColumns = @JoinColumn(name = "scope_id"))
    @Column(name = "market_id", nullable = false)
    private Set<UUID> marketIds = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResearchScopeStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    @Column(name = "scope_version", nullable = false)
    private long scopeVersion;

    @Column(name = "historical_earliest_cutoff")
    private Instant historicalEarliestCutoff;

    @Column(name = "historical_latest_cutoff")
    private Instant historicalLatestCutoff;

    @Column(name = "historical_max_observations")
    private Long historicalMaxObservations;

    @Column(name = "historical_max_export_rows")
    private Long historicalMaxExportRows;

    @Column(name = "historical_max_export_bytes")
    private Long historicalMaxExportBytes;

    @Column(name = "historical_max_duration_seconds")
    private Long historicalMaxDurationSeconds;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "research_scope_dataset_profile", joinColumns = @JoinColumn(name = "scope_id"))
    @Column(name = "dataset_profile", nullable = false)
    private Set<String> historicalDatasetProfiles = new HashSet<>();

    protected ResearchScope() {
    }

    private ResearchScope(UUID ownerUserId, Set<UUID> marketIds, Instant now, Instant expiresAt) {
        this(ownerUserId, marketIds, now, expiresAt, null);
    }

    private ResearchScope(UUID ownerUserId, Set<UUID> marketIds, Instant now, Instant expiresAt,
                          HistoricalResearchConstraints historicalConstraints) {
        if (ownerUserId == null || marketIds == null || marketIds.size() != 2 || now == null
                || (expiresAt != null && !expiresAt.isAfter(now))) {
            throw new IllegalArgumentException("owner, exactly two markets and valid timestamps are required");
        }
        this.id = UUID.randomUUID();
        this.ownerUserId = ownerUserId;
        this.marketIds = new HashSet<>(marketIds);
        this.status = ResearchScopeStatus.ACTIVE;
        this.createdAt = now;
        this.expiresAt = expiresAt;
        apply(historicalConstraints);
    }

    public static ResearchScope create(UUID ownerUserId, Set<UUID> marketIds, Instant now, Instant expiresAt) {
        return new ResearchScope(ownerUserId, marketIds, now, expiresAt);
    }

    public static ResearchScope create(UUID ownerUserId, Set<UUID> marketIds, Instant now, Instant expiresAt,
                                       HistoricalResearchConstraints historicalConstraints) {
        return new ResearchScope(ownerUserId, marketIds, now, expiresAt, historicalConstraints);
    }

    private void apply(HistoricalResearchConstraints constraints) {
        if (constraints == null) return;
        historicalEarliestCutoff = constraints.earliestCutoff();
        historicalLatestCutoff = constraints.latestCutoff();
        historicalMaxObservations = constraints.maxObservations();
        historicalMaxExportRows = constraints.maxExportRows();
        historicalMaxExportBytes = constraints.maxExportBytes();
        historicalMaxDurationSeconds = constraints.maxExecutionDurationSeconds();
        historicalDatasetProfiles = new HashSet<>(constraints.allowedDatasetProfiles());
    }

    public void revoke(Instant now) {
        if (status == ResearchScopeStatus.ACTIVE) {
            status = ResearchScopeStatus.REVOKED;
            revokedAt = now;
        }
    }

    public void expire(Instant now) {
        if (status == ResearchScopeStatus.ACTIVE) {
            status = ResearchScopeStatus.EXPIRED;
            revokedAt = now;
        }
    }

    public boolean effectiveAt(Instant now) {
        return status == ResearchScopeStatus.ACTIVE && now != null
                && (expiresAt == null || expiresAt.isAfter(now));
    }

    public UUID id() { return id; }
    public UUID ownerUserId() { return ownerUserId; }
    public Set<UUID> marketIds() { return Set.copyOf(marketIds); }
    public ResearchScopeStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant revokedAt() { return revokedAt; }
    public long scopeVersion() { return scopeVersion; }
    public java.util.Optional<HistoricalResearchConstraints> historicalConstraints() {
        if (historicalEarliestCutoff == null && historicalLatestCutoff == null
                && historicalMaxObservations == null && historicalMaxExportRows == null
                && historicalMaxExportBytes == null && historicalMaxDurationSeconds == null
                && historicalDatasetProfiles.isEmpty()) return java.util.Optional.empty();
        return java.util.Optional.of(new HistoricalResearchConstraints(historicalEarliestCutoff,
                historicalLatestCutoff, historicalMaxObservations, historicalMaxExportRows,
                historicalMaxExportBytes, historicalMaxDurationSeconds, historicalDatasetProfiles));
    }
}
