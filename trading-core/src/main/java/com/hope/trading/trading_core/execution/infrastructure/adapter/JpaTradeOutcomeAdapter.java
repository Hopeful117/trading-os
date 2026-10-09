package com.hope.trading.trading_core.execution.infrastructure.adapter;

import com.hope.trading.trading_core.execution.domain.aggregate.TradeOutcome;
import com.hope.trading.trading_core.shared.domain.model.TradePlanProvenance;
import com.hope.trading.trading_core.execution.domain.repository.TradeOutcomeRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.TradeOutcomeStatus;
import com.hope.trading.trading_core.execution.infrastructure.persistence.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional
public class JpaTradeOutcomeAdapter implements TradeOutcomeRepositoryPort {
    private final JpaTradeOutcomeRepository repository;

    public JpaTradeOutcomeAdapter(JpaTradeOutcomeRepository repository) {
        this.repository = repository;
    }

    @Override
    public TradeOutcome save(TradeOutcome value) {
        TradeOutcomeEntity entity = repository.findById(value.id()).orElseGet(TradeOutcomeEntity::new);
        entity.id = value.id(); entity.accountId = value.accountId(); entity.brokerAccountId = value.brokerAccountId();
        entity.executionIntentId = value.executionIntentId(); entity.tradePlanId = value.tradePlanId();
        entity.tradePlanVersion = value.tradePlanVersion(); entity.instrument = value.instrument();
        entity.side = value.side().name(); entity.quantity = value.quantity(); entity.entryPrice = value.entryPrice();
        entity.stopLossPrice = value.stopLossPrice();
        entity.takeProfitPrices = value.takeProfitPrices().stream().map(java.math.BigDecimal::toPlainString)
                .collect(java.util.stream.Collectors.joining(","));
        entity.expectedMonetaryRisk = value.expectedMonetaryRisk(); entity.riskRewardRatio = value.riskRewardRatio();
        entity.realizedPnl = value.realizedPnl(); entity.actualFee = value.actualFee(); entity.closedAt = value.closedAt();
        entity.status = value.status().name(); entity.createdAt = value.createdAt(); entity.updatedAt = value.updatedAt();
        entity.provenance = value.provenance().stream().map(item -> {
            TradeOutcomeProvenanceEntity result = new TradeOutcomeProvenanceEntity();
            result.opportunityId = item.opportunityId(); result.opportunityVersion = item.opportunityVersion();
            result.strategyMatchId = item.strategyMatchId(); result.strategyId = item.strategyId();
            result.strategyVersion = item.strategyVersion(); return result;
        }).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override @Transactional(readOnly = true)
    public Optional<TradeOutcome> findByExecutionIntentId(UUID id) {
        return repository.findByExecutionIntentId(id).map(this::toDomain);
    }

    @Override @Transactional(readOnly = true)
    public List<TradeOutcome> findByAccountId(UUID id) {
        return repository.findByAccountIdOrderByCreatedAtDesc(id).stream().map(this::toDomain).toList();
    }

    @Override @Transactional(readOnly = true)
    public Optional<TradeOutcome> findById(UUID id) { return repository.findById(id).map(this::toDomain); }

    private TradeOutcome toDomain(TradeOutcomeEntity value) {
        List<TradePlanProvenance> provenance = value.provenance == null ? List.of() : value.provenance.stream()
                .map(item -> new TradePlanProvenance(item.opportunityId, item.opportunityVersion,
                        item.strategyMatchId, item.strategyId, item.strategyVersion)).toList();
        return TradeOutcome.rehydrate(value.id, value.accountId, value.brokerAccountId, value.executionIntentId,
                value.tradePlanId, value.tradePlanVersion, provenance, value.instrument,
                com.hope.trading.trading_core.execution.domain.model.ExecutionParameters.Side.valueOf(value.side),
                value.quantity, value.entryPrice, value.stopLossPrice,
                value.takeProfitPrices == null || value.takeProfitPrices.isBlank() ? List.of() :
                        java.util.Arrays.stream(value.takeProfitPrices.split(",")).map(java.math.BigDecimal::new).toList(),
                value.expectedMonetaryRisk, value.riskRewardRatio, value.realizedPnl, value.actualFee,
                value.closedAt, TradeOutcomeStatus.valueOf(value.status), value.createdAt, value.updatedAt);
    }
}
