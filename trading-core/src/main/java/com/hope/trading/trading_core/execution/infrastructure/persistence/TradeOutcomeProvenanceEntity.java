package com.hope.trading.trading_core.execution.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public class TradeOutcomeProvenanceEntity {
    @Column(name = "opportunity_id", nullable = false, updatable = false) public UUID opportunityId;
    @Column(name = "opportunity_version", nullable = false, updatable = false) public long opportunityVersion;
    @Column(name = "strategy_match_id", updatable = false) public UUID strategyMatchId;
    @Column(name = "strategy_id", updatable = false) public UUID strategyId;
    @Column(name = "strategy_version", updatable = false) public Integer strategyVersion;
}
