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
        BrokerOrderEntity saved = repository.saveAndFlush(mapper.toEntity(order,entity));
        fillRepository.deleteAllByBrokerOrderId(order.id().value());
        fillRepository.saveAll(order.fills().stream()
                .map(fill -> mapper.toEntity(fill, order.id().value())).toList());
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
