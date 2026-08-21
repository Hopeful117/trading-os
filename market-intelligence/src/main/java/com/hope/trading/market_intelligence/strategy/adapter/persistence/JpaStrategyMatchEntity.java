package com.hope.trading.market_intelligence.strategy.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only persistence mapping for {@code strategy_matches}. No update
 * path exists through this entity's usage; rows are facts.
 */
@Entity
@Table(name = "strategy_matches")
public class JpaStrategyMatchEntity {

    @Id
    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "strategy_id", nullable = false)
    private UUID strategyId;

    @Column(name = "strategy_version", nullable = false)
    private int strategyVersion;

    @Column(name = "market_id", nullable = false)
    private UUID marketId;

    @Column(name = "analysis_execution_id", nullable = false)
    private UUID analysisExecutionId;

    @Column(name = "observation_id", nullable = false)
    private UUID observationId;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "context_digest", nullable = false, length = 64)
    private String contextDigest;

    @Column(name = "condition_results", nullable = false, columnDefinition = "text")
    private String conditionResults;

    @Column(name = "matched_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant matchedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    public UUID getMatchId() { return matchId; }

    public void setMatchId(UUID matchId) { this.matchId = matchId; }

    public UUID getStrategyId() { return strategyId; }

    public void setStrategyId(UUID strategyId) { this.strategyId = strategyId; }

    public int getStrategyVersion() { return strategyVersion; }

    public void setStrategyVersion(int strategyVersion) { this.strategyVersion = strategyVersion; }

    public UUID getMarketId() { return marketId; }

    public void setMarketId(UUID marketId) { this.marketId = marketId; }

    public UUID getAnalysisExecutionId() { return analysisExecutionId; }

    public void setAnalysisExecutionId(UUID analysisExecutionId) {
        this.analysisExecutionId = analysisExecutionId;
    }

    public UUID getObservationId() { return observationId; }

    public void setObservationId(UUID observationId) { this.observationId = observationId; }

    public String getDirection() { return direction; }

    public void setDirection(String direction) { this.direction = direction; }

    public String getContextDigest() { return contextDigest; }

    public void setContextDigest(String contextDigest) { this.contextDigest = contextDigest; }

    public String getConditionResults() { return conditionResults; }

    public void setConditionResults(String conditionResults) {
        this.conditionResults = conditionResults;
    }

    public Instant getMatchedAt() { return matchedAt; }

    public void setMatchedAt(Instant matchedAt) { this.matchedAt = matchedAt; }

    public Instant getCreatedAt() { return createdAt; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
