package com.hope.trading.broker_service.broker.infrastructure.provider.kraken;

import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.*;
import com.hope.trading.broker_service.broker.domain.provider.BrokerProvider;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.capability.KrakenCapabilities;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.capability.KrakenRiskSnapshotCapability;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import org.springframework.stereotype.Component;

@Component
public final class KrakenBrokerProvider implements BrokerProvider {
    private final KrakenCapabilities capabilities;
    private final KrakenRiskSnapshotCapability riskSnapshots;
    public KrakenBrokerProvider(KrakenCapabilities capabilities,KrakenRiskSnapshotCapability riskSnapshots){this.capabilities=capabilities;this.riskSnapshots=riskSnapshots;}
    public BrokerProviderId id(){return BrokerProviderId.KRAKEN;}
    public <T> java.util.Optional<T> capability(Class<T> type){if(type.isInstance(capabilities))return java.util.Optional.of(type.cast(capabilities));if(type.isInstance(riskSnapshots))return java.util.Optional.of(type.cast(riskSnapshots));return java.util.Optional.empty();}
}
