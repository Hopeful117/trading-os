package com.hope.trading.broker_service.broker;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerUnavailableException;
import com.hope.trading.broker_service.broker.infrastructure.monitoring.BrokerProviderHealth;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client.*;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.kraken.config.KrakenProperties;
import java.time.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KrakenResilientClientTest {
    @Test void retriesReadsButNeverRetriesWrites(){
        AtomicInteger calls=new AtomicInteger();KrakenProviderClient delegate=(p,b,c)->{if(calls.incrementAndGet()<3)throw unavailable();return JsonNodeFactory.instance.objectNode();};
        var readClient=client(delegate,new MutableClock());try(var credentials=credentials()){readClient.privatePost("/0/private/Balance",Map.of(),credentials);}assertThat(calls).hasValue(3);
        calls.set(0);KrakenProviderClient failing=(p,b,c)->{calls.incrementAndGet();throw unavailable();};var writeClient=client(failing,new MutableClock());
        try(var credentials=credentials()){assertThatThrownBy(()->writeClient.privatePost("/0/private/AddOrder",Map.of(),credentials)).isInstanceOf(BrokerUnavailableException.class);}assertThat(calls).hasValue(1);
    }
    @Test void opensCircuitAfterFiveFailedOperationsAndRecoversAfterWindow(){
        MutableClock clock=new MutableClock();AtomicInteger calls=new AtomicInteger();KrakenProviderClient failing=(p,b,c)->{calls.incrementAndGet();throw unavailable();};var client=client(failing,clock);
        try(var credentials=credentials()){for(int i=0;i<5;i++)assertThatThrownBy(()->client.privatePost("/0/private/AddOrder",Map.of(),credentials)).isInstanceOf(BrokerUnavailableException.class);assertThat(client.status()).isEqualTo(BrokerProviderHealth.Status.UNAVAILABLE);assertThatThrownBy(()->client.privatePost("/0/private/AddOrder",Map.of(),credentials)).isInstanceOf(BrokerUnavailableException.class);assertThat(calls).hasValue(5);clock.advance(Duration.ofSeconds(31));assertThat(client.status()).isEqualTo(BrokerProviderHealth.Status.DEGRADED);}
    }
    private KrakenResilientClient client(KrakenProviderClient delegate,Clock clock){KrakenProperties p=new KrakenProperties();p.setReadMaxAttempts(3);p.setRequestsPerSecond(100);return new KrakenResilientClient(delegate,clock,p);}
    private BrokerUnavailableException unavailable(){return new BrokerUnavailableException("down",new RuntimeException());}
    private CredentialMaterial credentials(){return new CredentialMaterial("12345678".toCharArray(),"MTIzNDU2Nzg5MDEyMzQ1Ng==".toCharArray(),null);}
    static final class MutableClock extends Clock {private Instant now=Instant.parse("2026-08-01T00:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}void advance(Duration duration){now=now.plus(duration);}}
}
