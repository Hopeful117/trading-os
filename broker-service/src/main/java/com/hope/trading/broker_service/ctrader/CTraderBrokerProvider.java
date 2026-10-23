package com.hope.trading.broker_service.ctrader;

import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.AuthoritativeSnapshotCapability;
import com.hope.trading.broker_service.broker.domain.provider.BrokerProvider;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.ctrader.synchronization.CTraderAuthoritativeSnapshotCapability;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public final class CTraderBrokerProvider implements BrokerProvider {
    private final CTraderAuthoritativeSnapshotCapability snapshots;

    public CTraderBrokerProvider(CTraderAuthoritativeSnapshotCapability snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public BrokerProviderId id() {
        return BrokerProviderId.CTRADER;
    }

    @Override
    public <T> Optional<T> capability(Class<T> type) {
        return type.isInstance(snapshots) ? Optional.of(type.cast(snapshots)) : Optional.empty();
    }
}
