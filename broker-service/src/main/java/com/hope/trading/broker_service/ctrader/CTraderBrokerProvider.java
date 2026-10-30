package com.hope.trading.broker_service.ctrader;

import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.AuthoritativeSnapshotCapability;
import com.hope.trading.broker_service.broker.domain.provider.BrokerProvider;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.ctrader.synchronization.CTraderAuthoritativeSnapshotCapability;
import com.hope.trading.broker_service.ctrader.execution.CTraderExecutionCapability;
import com.hope.trading.broker_service.ctrader.position.CTraderPositionManagementCapability;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

@Component
public final class CTraderBrokerProvider implements BrokerProvider {
    private final CTraderAuthoritativeSnapshotCapability snapshots;
    private final CTraderExecutionCapability execution;
    private final CTraderTechnicalCapabilities technicalCapabilities;
    private final CTraderPositionManagementCapability positionManagement;

    @Autowired
    public CTraderBrokerProvider(CTraderAuthoritativeSnapshotCapability snapshots,
                                 CTraderExecutionCapability execution,
                                 CTraderTechnicalCapabilities technicalCapabilities,
                                 CTraderPositionManagementCapability positionManagement) {
        this.snapshots = snapshots;
        this.execution = execution;
        this.technicalCapabilities = technicalCapabilities;
        this.positionManagement = positionManagement;
    }

    public CTraderBrokerProvider(CTraderAuthoritativeSnapshotCapability snapshots) {
        this(snapshots, null, null, null);
    }

    @Override
    public BrokerProviderId id() {
        return BrokerProviderId.CTRADER;
    }

    @Override
    public <T> Optional<T> capability(Class<T> type) {
        if (type.isInstance(snapshots)) return Optional.of(type.cast(snapshots));
        if (type.isInstance(technicalCapabilities)) return Optional.of(type.cast(technicalCapabilities));
        if (positionManagement != null && type.isInstance(positionManagement)) return Optional.of(type.cast(positionManagement));
        return execution != null && execution.enabled() && type.isInstance(execution)
                ? Optional.of(type.cast(execution)) : Optional.empty();
    }
}
