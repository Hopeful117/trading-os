package com.hope.trading.market_intelligence.strategy.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository;
import com.hope.trading.market_intelligence.strategy.domain.ConditionResult;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatchIdentity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Append-only persistence adapter for StrategyMatch facts. Condition results
 * are serialized once to an immutable JSON payload; no update or delete
 * semantics exist.
 */
@Repository
public class JpaStrategyMatchRepository implements StrategyMatchRepository {

    private static final String PAYLOAD_READ_FAILURE = "Unreadable condition results payload";

    private final SpringDataStrategyMatchRepository jpa;
    private final ObjectMapper objectMapper;

    public JpaStrategyMatchRepository(
            SpringDataStrategyMatchRepository jpa, ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<StrategyMatch> findById(UUID matchId) {
        return jpa.findById(matchId).map(this::toDomain);
    }

    @Override
    public Optional<StrategyMatch> findByIdentity(StrategyMatchIdentity identity) {
        return jpa.findByStrategyIdAndStrategyVersionAndMarketIdAndAnalysisExecutionIdAndContextDigest(
                        identity.strategyId(), identity.strategyVersion(),
                        identity.marketId(), identity.analysisExecutionId(),
                        identity.contextDigest())
                .stream().findFirst().map(this::toDomain);
    }

    @Override
    public List<StrategyMatch> findByAnalysisExecutionId(UUID analysisExecutionId) {
        return jpa.findByAnalysisExecutionId(analysisExecutionId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public StrategyMatch save(StrategyMatch match) {
        jpa.save(toEntity(match));
        return match;
    }

    private JpaStrategyMatchEntity toEntity(StrategyMatch match) {
        JpaStrategyMatchEntity entity = new JpaStrategyMatchEntity();
        entity.setMatchId(match.matchId());
        entity.setStrategyId(match.strategyId().value());
        entity.setStrategyVersion(match.strategyVersion());
        entity.setMarketId(match.marketId());
        entity.setAnalysisExecutionId(match.analysisExecutionId());
        entity.setObservationId(match.observationId());
        entity.setDirection(match.direction().name());
        entity.setContextDigest(match.contextDigest());
        entity.setConditionResults(serialize(match));
        entity.setMatchedAt(match.matchedAt());
        entity.setCreatedAt(match.createdAt());
        return entity;
    }

    private StrategyMatch toDomain(JpaStrategyMatchEntity entity) {
        return StrategyMatch.rehydrate(
                entity.getMatchId(),
                new StrategyId(entity.getStrategyId()),
                entity.getStrategyVersion(),
                entity.getMarketId(),
                entity.getAnalysisExecutionId(),
                entity.getObservationId(),
                MatchedDirection.valueOf(entity.getDirection()),
                entity.getContextDigest(),
                deserialize(entity),
                entity.getMatchedAt(),
                entity.getCreatedAt());
    }

    private String serialize(StrategyMatch match) {
        try {
            return objectMapper.writeValueAsString(match.conditionResults());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Condition results could not be serialized", exception);
        }
    }

    private List<ConditionResult> deserialize(JpaStrategyMatchEntity entity) {
        try {
            return List.of(objectMapper.readValue(
                    entity.getConditionResults(), ConditionResult[].class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(PAYLOAD_READ_FAILURE, exception);
        }
    }
}
