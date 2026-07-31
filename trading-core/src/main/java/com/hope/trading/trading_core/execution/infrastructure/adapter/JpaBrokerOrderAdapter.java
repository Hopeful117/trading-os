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
    private final BrokerOrderMapper mapper=new BrokerOrderMapper();
    public JpaBrokerOrderAdapter(JpaBrokerOrderRepository repository){this.repository=repository;}
    @Override public BrokerOrder save(BrokerOrder order){
        BrokerOrderEntity entity=repository.findById(order.id().value()).orElseGet(BrokerOrderEntity::new);
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(order,entity)));
    }
    @Override @Transactional(readOnly=true) public Optional<BrokerOrder> findById(BrokerOrderId id){
        return repository.findById(id.value()).map(mapper::toDomain);
    }
    @Override @Transactional(readOnly=true) public Optional<BrokerOrder> findByIntentId(ExecutionIntentId id){
        return repository.findByIntentId(id.value()).map(mapper::toDomain);
    }
}
