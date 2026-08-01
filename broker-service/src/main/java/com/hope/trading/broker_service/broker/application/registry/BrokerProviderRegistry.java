package com.hope.trading.broker_service.broker.application.registry;

import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.UnsupportedBrokerProviderException;
import com.hope.trading.broker_service.broker.domain.provider.BrokerProvider;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public final class BrokerProviderRegistry {
    private final Map<BrokerProviderId,BrokerProvider> providers;
    public BrokerProviderRegistry(List<BrokerProvider> providers){
        try { this.providers=providers.stream().collect(Collectors.toUnmodifiableMap(BrokerProvider::id,Function.identity())); }
        catch(IllegalStateException duplicate){ throw new IllegalArgumentException("Duplicate broker provider",duplicate); }
    }
    public BrokerProvider resolve(BrokerProviderId id){
        BrokerProvider provider=providers.get(Objects.requireNonNull(id));
        if(provider==null)throw new UnsupportedBrokerProviderException("Unsupported broker provider: "+id);
        return provider;
    }
    public Set<BrokerProviderId> supportedProviders(){return providers.keySet();}
}
