package com.hope.trading.broker_service.broker.domain.capability;

import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import java.util.List;
import java.util.UUID;

public final class BrokerCapabilities {
    private BrokerCapabilities() {}
    public interface AuthenticationCapability { void verify(UUID brokerAccountId); }
    public interface AccountCapability { AccountSnapshot account(UUID brokerAccountId); }
    public interface PositionCapability { List<PositionSnapshot> positions(UUID brokerAccountId); }
    public interface OrderCapability { List<OrderSnapshot> orders(UUID brokerAccountId); void cancel(UUID brokerAccountId,String externalOrderId); }
    public interface ExecutionCapability { ExecutionResult execute(ExecutionRequest request); }
    public interface ReconciliationCapability { ReconciliationResult reconcile(ReconciliationRequest request); }
}
