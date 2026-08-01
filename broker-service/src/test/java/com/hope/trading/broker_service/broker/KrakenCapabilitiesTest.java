package com.hope.trading.broker_service.broker;

import com.fasterxml.jackson.databind.*;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication.ProviderCredentialSession;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.capability.KrakenCapabilities;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client.KrakenProviderClient;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.mapper.KrakenOrderMapper;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import java.math.BigDecimal;import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;

class KrakenCapabilitiesTest {
    private static final UUID ACCOUNT=UUID.randomUUID(),INTENT=UUID.randomUUID(),ATTEMPT=UUID.randomUUID();
    private final ObjectMapper json=new ObjectMapper();
    @Test void submitsOrderWithStableClientOrderId(){var client=new StubClient(json);var capabilities=capabilities(client);ExecutionRequest request=new ExecutionRequest(INTENT,ATTEMPT,"stable-key",ACCOUNT,"XBTUSD",Side.BUY,OrderType.MARKET,BigDecimal.ONE,null);assertThat(capabilities.execute(request)).isInstanceOf(Acknowledged.class);String first=client.lastParameters.get("cl_ord_id");capabilities.execute(request);assertThat(client.lastParameters.get("cl_ord_id")).isEqualTo(first);}
    @Test void reconciliationFindsOrderByIdempotencyKey(){var client=new StubClient(json);var capabilities=capabilities(client);var request=new ExecutionRequest(INTENT,ATTEMPT,"stable-key",ACCOUNT,"XBTUSD",Side.BUY,OrderType.MARKET,BigDecimal.ONE,null);capabilities.execute(request);client.clientOrderId=client.lastParameters.get("cl_ord_id");assertThat(capabilities.reconcile(new ReconciliationRequest(INTENT,ATTEMPT,"stable-key",ACCOUNT))).isInstanceOf(ReconciledOrder.class);}
    @Test void unavailableSubmissionRemainsUnknown(){KrakenProviderClient client=(p,b,c)->{throw new com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerUnavailableException("down",new RuntimeException());};assertThat(capabilities(client).execute(new ExecutionRequest(INTENT,ATTEMPT,"key",ACCOUNT,"XBTUSD",Side.BUY,OrderType.MARKET,BigDecimal.ONE,null))).isInstanceOf(Unknown.class);}
    @Test void ambiguousProviderResponseRemainsUnknown(){KrakenProviderClient client=(p,b,c)->{throw new com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerProtocolException("missing txid");};assertThat(capabilities(client).execute(new ExecutionRequest(INTENT,ATTEMPT,"key",ACCOUNT,"XBTUSD",Side.BUY,OrderType.MARKET,BigDecimal.ONE,null))).isEqualTo(new Unknown("BROKER_RESPONSE_UNCERTAIN"));}
    @Test void explicitInvalidOrderIsRejected(){KrakenProviderClient client=(p,b,c)->{throw new com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.InvalidOrderException("invalid volume");};assertThat(capabilities(client).execute(new ExecutionRequest(INTENT,ATTEMPT,"key",ACCOUNT,"XBTUSD",Side.BUY,OrderType.MARKET,BigDecimal.ONE,null))).isInstanceOf(Rejected.class);}
    private KrakenCapabilities capabilities(KrakenProviderClient client){ProviderCredentialSession session=new ProviderCredentialSession(){public <T>T withCredentials(UUID id,java.util.function.Function<CredentialMaterial,T> operation){try(var credentials=new CredentialMaterial("12345678".toCharArray(),"MTIzNDU2Nzg5MDEyMzQ1Ng==".toCharArray(),null)){return operation.apply(credentials);}}};return new KrakenCapabilities(session,client,new KrakenOrderMapper(),Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"),ZoneOffset.UTC));}
    static final class StubClient implements KrakenProviderClient {private final ObjectMapper json;Map<String,String> lastParameters=Map.of();String clientOrderId;StubClient(ObjectMapper json){this.json=json;}public JsonNode privatePost(String path,Map<String,String> parameters,CredentialMaterial ignored){lastParameters=Map.copyOf(parameters);try{if(path.endsWith("AddOrder"))return json.readTree("{\"txid\":[\"ORDER-1\"]}");if(path.endsWith("OpenOrders")&&clientOrderId!=null)return json.readTree("{\"open\":{\"ORDER-1\":{\"cl_ord_id\":\""+clientOrderId+"\",\"status\":\"open\",\"vol\":\"1\",\"vol_exec\":\"0\"}}}");if(path.endsWith("OpenOrders"))return json.readTree("{\"open\":{}}");return json.readTree("{\"closed\":{}}");}catch(Exception e){throw new RuntimeException(e);}}}
}
