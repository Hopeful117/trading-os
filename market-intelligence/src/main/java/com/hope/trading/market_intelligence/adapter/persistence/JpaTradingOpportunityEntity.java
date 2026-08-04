package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trading_opportunity_versions")
@IdClass(JpaTradingOpportunityId.class)
class JpaTradingOpportunityEntity {
    @Id @Column(name = "opportunity_id") UUID opportunityId;
    @Id @Column(name = "version") long version;
    @Column(name = "status", nullable = false, length = 30) String status;
    @Column(name = "instrument", nullable = false, length = 120) String instrument;
    @Column(name = "direction", nullable = false, length = 20) String direction;
    @Column(name = "scenario", nullable = false, length = 120) String scenario;
    @Column(name = "timeframe", nullable = false, length = 40) String timeframe;
    @Column(name = "evaluated_at", nullable = false) Instant evaluatedAt;
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT") String payload;
}

record JpaTradingOpportunityId(UUID opportunityId, long version) implements Serializable { }
