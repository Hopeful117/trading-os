package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerUnavailableException;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.broker.infrastructure.monitoring.BrokerProviderHealth;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.kraken.config.KrakenProperties;
import java.time.*;import java.util.Map;import java.util.concurrent.Semaphore;import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Qualifier;import org.springframework.context.annotation.Primary;import org.springframework.stereotype.Component;

@Primary @Component
public final class KrakenResilientClient implements KrakenProviderClient, BrokerProviderHealth {
    private static final java.util.Set<String> SAFE_READS=java.util.Set.of("/0/private/Balance","/0/private/OpenPositions","/0/private/OpenOrders","/0/private/ClosedOrders","/0/private/TradeBalance","/0/private/TradesHistory","/0/private/Ledgers");
    private final KrakenProviderClient delegate;private final Clock clock;private final Semaphore bulkhead=new Semaphore(20);
    private final int readMaxAttempts,requestsPerSecond;private long rateWindowSecond=-1;private int rateWindowRequests;
    private final AtomicInteger consecutiveFailures=new AtomicInteger();private volatile Instant openUntil=Instant.EPOCH;
    public KrakenResilientClient(@Qualifier("krakenRestProviderClient") KrakenProviderClient delegate,Clock clock,KrakenProperties properties){this.delegate=delegate;this.clock=clock;this.readMaxAttempts=Math.max(1,properties.getReadMaxAttempts());this.requestsPerSecond=Math.max(1,properties.getRequestsPerSecond());}
    public JsonNode privatePost(String path,Map<String,String> parameters,CredentialMaterial credentials){
        if(clock.instant().isBefore(openUntil))throw new BrokerUnavailableException("Kraken circuit breaker is open",null);
        if(!bulkhead.tryAcquire())throw new BrokerUnavailableException("Kraken communication bulkhead is full",null);
        try{
            int attempts=SAFE_READS.contains(path)?readMaxAttempts:1;
            for(int attempt=1;;attempt++)try{acquireRatePermit();JsonNode result=delegate.privatePost(path,parameters,credentials);consecutiveFailures.set(0);return result;}
            catch(BrokerUnavailableException failure){if(attempt>=attempts)throw failure;}
        }
        catch(BrokerUnavailableException failure){if(consecutiveFailures.incrementAndGet()>=5)openUntil=clock.instant().plusSeconds(30);throw failure;}
        finally{bulkhead.release();}
    }
    private synchronized void acquireRatePermit(){
        long second=clock.instant().getEpochSecond();
        if(second!=rateWindowSecond){rateWindowSecond=second;rateWindowRequests=0;}
        if(rateWindowRequests>=requestsPerSecond)throw new com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerRateLimitException("Local Kraken rate limit exceeded");
        rateWindowRequests++;
    }
    public BrokerProviderId providerId(){return BrokerProviderId.KRAKEN;}
    public Status status(){if(clock.instant().isBefore(openUntil))return Status.UNAVAILABLE;return consecutiveFailures.get()==0?Status.AVAILABLE:Status.DEGRADED;}
}
