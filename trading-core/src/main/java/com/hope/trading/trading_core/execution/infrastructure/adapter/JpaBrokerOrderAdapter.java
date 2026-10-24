package com.hope.trading.trading_core.execution.infrastructure.adapter;

import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.repository.BrokerOrderRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.execution.infrastructure.mapper.BrokerOrderMapper;
import com.hope.trading.trading_core.execution.infrastructure.persistence.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Component
@Transactional
public class JpaBrokerOrderAdapter implements BrokerOrderRepositoryPort {
    private final JpaBrokerOrderRepository repository;
    private final JpaBrokerFillRepository fillRepository;
    private final BrokerOrderMapper mapper=new BrokerOrderMapper();
    public JpaBrokerOrderAdapter(JpaBrokerOrderRepository repository, JpaBrokerFillRepository fillRepository){
        this.repository=repository; this.fillRepository=fillRepository;
    }
    @Override public BrokerOrder save(BrokerOrder order){
        BrokerOrderEntity entity=repository.findById(order.id().value()).orElseGet(BrokerOrderEntity::new);
        // Fills are immutable execution evidence and may be referenced by provenance.
        // Existing rows may be replayed identically, but conflicting rewrites are rejected.
        List<BrokerFillEntity> newFills = new ArrayList<>();
        for (BrokerOrder.Fill fill : order.fills()) {
            Optional<BrokerFillEntity> existing = fillRepository.findById(fill.fillId());
            if (existing.isPresent()) {
                BrokerFillEntity value = existing.get();
                if (!order.id().value().equals(value.brokerOrderId)
                        || value.quantity.compareTo(fill.quantity()) != 0
                        || value.price.compareTo(fill.price()) != 0
                        || value.fee.compareTo(fill.fee()) != 0
                        || !value.executedAt.equals(fill.executedAt())) {
                    throw new IllegalStateException("Broker fill is immutable: " + fill.fillId());
                }
            } else {
                newFills.add(mapper.toEntity(fill, order.id().value()));
            }
        }
        BrokerOrderEntity saved = repository.saveAndFlush(mapper.toEntity(order,entity));
        if (!newFills.isEmpty()) fillRepository.saveAll(newFills);
        fillRepository.flush();
        return mapper.toDomain(saved,
                fillRepository.findAllByBrokerOrderIdOrderByExecutedAtAsc(order.id().value()));
    }
    @Override @Transactional(readOnly=true) public Optional<BrokerOrder> findById(BrokerOrderId id){
        return repository.findById(id.value()).map(entity -> mapper.toDomain(entity,
                fillRepository.findAllByBrokerOrderIdOrderByExecutedAtAsc(id.value())));
    }
    @Override @Transactional(readOnly=true) public Optional<BrokerOrder> findByIntentId(ExecutionIntentId id){
        return repository.findByIntentId(id.value()).map(entity -> mapper.toDomain(entity,
                fillRepository.findAllByBrokerOrderIdOrderByExecutedAtAsc(entity.id)));
    }
}
