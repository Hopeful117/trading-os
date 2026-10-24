package com.hope.trading.trading_core.execution.infrastructure.persistence;

import com.hope.trading.trading_core.execution.application.port.TradeExecutionProvenanceStore;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaTradeExecutionProvenanceStore implements TradeExecutionProvenanceStore {
    private final JpaTradeExecutionProvenanceRepository repository;

    public JpaTradeExecutionProvenanceStore(JpaTradeExecutionProvenanceRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Provenance> findByBrokerOrderIdAndFillId(UUID brokerOrderId, String fillId) {
        return repository.findByBrokerOrderIdAndFillId(brokerOrderId, fillId).map(this::toDomain);
    }

    @Override
    public List<Provenance> findAllByTradeIdsAndExecutedAtBetween(List<UUID> tradeIds, Instant start, Instant end) {
        return repository.findAllByTradeIdInAndExecutedAtGreaterThanEqualAndExecutedAtLessThanEqual(
                        tradeIds, start, end).stream().map(this::toDomain).toList();
    }

    @Override
    public void save(Provenance provenance) {
        TradeExecutionProvenanceEntity entity = new TradeExecutionProvenanceEntity();
        entity.id = provenance.id();
        entity.tradeId = provenance.tradeId();
        entity.executionAttemptId = provenance.executionAttemptId();
        entity.brokerOrderId = provenance.brokerOrderId();
        entity.fillId = provenance.fillId();
        entity.executedAt = provenance.executedAt();
        entity.executionMode = provenance.executionMode();
        entity.executionPurpose = provenance.executionPurpose();
        repository.saveAndFlush(entity);
    }

    private Provenance toDomain(TradeExecutionProvenanceEntity entity) {
        return new Provenance(entity.id, entity.tradeId, entity.executionAttemptId,
                entity.brokerOrderId, entity.fillId, entity.executedAt,
                entity.executionMode, entity.executionPurpose);
    }
}
