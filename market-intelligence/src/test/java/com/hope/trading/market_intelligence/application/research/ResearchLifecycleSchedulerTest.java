package com.hope.trading.market_intelligence.application.research;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchLifecycleSchedulerTest {
    @Test
    void delegatesScheduledRunsToTheLifecycleService() {
        ResearchLifecycleService lifecycle = mock(ResearchLifecycleService.class);
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        when(discoveryClient.getInstances("market-data")).thenReturn(List.of(mock(ServiceInstance.class)));

        new ResearchLifecycleScheduler(lifecycle, discoveryClient).run();

        verify(lifecycle).runOnce();
    }

    @Test
    void skipsWhenMarketDataIsNotDiscoverable() {
        ResearchLifecycleService lifecycle = mock(ResearchLifecycleService.class);
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        when(discoveryClient.getInstances("market-data")).thenReturn(List.of());

        new ResearchLifecycleScheduler(lifecycle, discoveryClient).run();

        verify(lifecycle, never()).runOnce();
    }

    @Test
    void resumesWhenMarketDataBecomesDiscoverable() {
        ResearchLifecycleService lifecycle = mock(ResearchLifecycleService.class);
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        when(discoveryClient.getInstances("market-data"))
                .thenReturn(List.of())
                .thenReturn(List.of(mock(ServiceInstance.class)));
        ResearchLifecycleScheduler scheduler = new ResearchLifecycleScheduler(lifecycle, discoveryClient);

        scheduler.run();
        scheduler.run();

        verify(lifecycle).runOnce();
    }

    @Test
    void skipsWhenDiscoveryCheckFails() {
        ResearchLifecycleService lifecycle = mock(ResearchLifecycleService.class);
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        doThrow(new IllegalStateException("discovery unavailable"))
                .when(discoveryClient).getInstances("market-data");

        new ResearchLifecycleScheduler(lifecycle, discoveryClient).run();

        verify(lifecycle, never()).runOnce();
    }
}
