package com.hope.trading.market_data.model;

import com.hope.trading.market_data.dto.ValuationSnapshotBatchResponse.SnapshotStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "valuation_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValuationSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long version;
    @Column(nullable = false, unique = true)
    private UUID snapshotId;
    @Column(nullable = false)
    private String reportingCurrency;
    @Column(nullable = false)
    private Instant valuationTimestamp;
    @Column(nullable = false)
    private Instant capturedAt;
    @Column(nullable = false, length = 64)
    private String policyVersion;
    @Column(nullable = false, length = 64)
    private String maxObservationAge;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SnapshotStatus status;
    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ValuationFact> facts = new ArrayList<>();

    public ValuationSnapshot(UUID snapshotId, String reportingCurrency, Instant valuationTimestamp,
                             Instant capturedAt, String policyVersion, String maxObservationAge,
                             SnapshotStatus status) {
        this.snapshotId = snapshotId;
        this.reportingCurrency = reportingCurrency;
        this.valuationTimestamp = valuationTimestamp;
        this.capturedAt = capturedAt;
        this.policyVersion = policyVersion;
        this.maxObservationAge = maxObservationAge;
        this.status = status;
    }

    public void addFact(ValuationFact fact) {
        facts.add(fact);
        fact.attachTo(this);
    }
}
