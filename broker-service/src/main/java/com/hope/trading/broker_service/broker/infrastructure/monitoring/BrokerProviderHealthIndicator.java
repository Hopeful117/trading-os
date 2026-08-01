package com.hope.trading.broker_service.broker.infrastructure.monitoring;

import com.hope.trading.broker_service.broker.application.registry.BrokerProviderRegistry;
import java.util.List;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("brokerProviders")
public final class BrokerProviderHealthIndicator implements HealthIndicator {
    private final BrokerProviderRegistry registry;private final List<BrokerProviderHealth> providers;
    public BrokerProviderHealthIndicator(BrokerProviderRegistry registry,List<BrokerProviderHealth> providers){this.registry=registry;this.providers=List.copyOf(providers);}
    public Health health(){
        if(registry.supportedProviders().isEmpty())return Health.down().withDetail("reason","no provider registered").build();
        var details=new java.util.TreeMap<String,String>();
        providers.forEach(provider->details.put(provider.providerId().name(),provider.status().name()));
        boolean unavailable=providers.stream().anyMatch(provider->provider.status()==BrokerProviderHealth.Status.UNAVAILABLE);
        Health.Builder result=unavailable?Health.down():Health.up();
        return result.withDetail("providers",details).build();
    }
}
