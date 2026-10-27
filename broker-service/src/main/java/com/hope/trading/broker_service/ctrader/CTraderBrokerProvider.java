package com.hope.trading.broker_service.ctrader;

import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.AuthoritativeSnapshotCapability;
import com.hope.trading.broker_service.broker.domain.provider.BrokerProvider;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.ctrader.synchronization.CTraderAuthoritativeSnapshotCapability;
import com.hope.trading.broker_service.ctrader.execution.CTraderExecutionCapability;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

@Component
public final class CTraderBrokerProvider implements BrokerProvider {
    private final CTraderAuthoritativeSnapshotCapability snapshots;
    private final CTraderExecutionCapability execution;
    private final CTraderTechnicalCapabilities technicalCapabilities;

    @Autowired
    public CTraderBrokerProvider(CTraderAuthoritativeSnapshotCapability snapshots,
                                 CTraderExecutionCapability execution,
                                 CTraderTechnicalCapabilities technicalCapabilities) {
        this.snapshots = snapshots;
        this.execution = execution;
        this.technicalCapabilities = technicalCapabilities;
    }

    public CTraderBrokerProvider(CTraderAuthoritativeSnapshotCapability snapshots) {
        this(snapshots, null, null);
    }

    @Override
    public BrokerProviderId id() {
        return BrokerProviderId.CTRADER;
    }

    @Override
    public <T> Optional<T> capability(Class<T> type) {
        if (type.isInstance(snapshots)) return Optional.of(type.cast(snapshots));
        if (type.isInstance(technicalCapabilities)) return Optional.of(type.cast(technicalCapabilities));
        return type.isInstance(execution) ? Optional.of(type.cast(execution)) : Optional.empty();
    }
}
