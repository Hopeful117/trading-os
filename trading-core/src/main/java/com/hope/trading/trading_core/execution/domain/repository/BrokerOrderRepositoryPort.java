package com.hope.trading.trading_core.execution.domain.repository;

import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import java.util.*;

public interface BrokerOrderRepositoryPort {
    BrokerOrder save(BrokerOrder order);
    Optional<BrokerOrder> findById(BrokerOrderId id);
    Optional<BrokerOrder> findByIntentId(ExecutionIntentId intentId);
}
