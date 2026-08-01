package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.*;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.*;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication.ProviderCredentialSession;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client.KrakenProviderClient;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.mapper.KrakenOrderMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public final class KrakenCapabilities implements AuthenticationCapability,AccountCapability,
        PositionCapability,OrderCapability,ExecutionCapability,ReconciliationCapability {
    private final ProviderCredentialSession sessions; private final KrakenProviderClient client;
    private final KrakenOrderMapper mapper; private final Clock clock;
    public KrakenCapabilities(ProviderCredentialSession sessions,KrakenProviderClient client,KrakenOrderMapper mapper,Clock clock){this.sessions=sessions;this.client=client;this.mapper=mapper;this.clock=clock;}
    public void verify(UUID accountId){sessions.withCredentials(accountId,c->{client.privatePost("/0/private/Balance",Map.of(),c);return null;});}
    public AccountSnapshot account(UUID accountId){return sessions.withCredentials(accountId,c->{
        JsonNode result=client.privatePost("/0/private/Balance",Map.of(),c);Map<String,BigDecimal> balances=new TreeMap<>();
        result.fields().forEachRemaining(entry->balances.put(entry.getKey(),new BigDecimal(entry.getValue().asText("0"))));
        return new AccountSnapshot(accountId,balances,clock.instant());});}
    public List<PositionSnapshot> positions(UUID accountId){return sessions.withCredentials(accountId,c->{
        JsonNode result=client.privatePost("/0/private/OpenPositions",Map.of(),c);List<PositionSnapshot> positions=new ArrayList<>();
        result.fields().forEachRemaining(entry->{JsonNode p=entry.getValue();BigDecimal quantity=new BigDecimal(p.path("vol").asText("0"));if("sell".equals(p.path("type").asText()))quantity=quantity.negate();positions.add(new PositionSnapshot(p.path("pair").asText(entry.getKey()),quantity,new BigDecimal(p.path("cost").asText("0")).divide(new BigDecimal(p.path("vol").asText("1")),java.math.MathContext.DECIMAL64),clock.instant()));});
        return List.copyOf(positions);});}
    public List<OrderSnapshot> orders(UUID accountId){return sessions.withCredentials(accountId,c->readOrders(c,null));}
    public void cancel(UUID accountId,String externalOrderId){sessions.withCredentials(accountId,c->{client.privatePost("/0/private/CancelOrder",Map.of("txid",required(externalOrderId)),c);return null;});}
    public ExecutionResult execute(ExecutionRequest request){
        try{return sessions.withCredentials(request.brokerAccountId(),c->{Map<String,String> body=new LinkedHashMap<>();
            body.put("pair",request.instrument());body.put("type",request.side().name().toLowerCase());body.put("ordertype",request.orderType().name().toLowerCase());body.put("volume",request.quantity().toPlainString());body.put("cl_ord_id",clientOrderId(request.idempotencyKey()));
            if(request.limitPrice()!=null)body.put("price",request.limitPrice().toPlainString());JsonNode result=client.privatePost("/0/private/AddOrder",body,c);
            JsonNode txids=result.path("txid");if(!txids.isArray()||txids.isEmpty())throw new BrokerProtocolException("Kraken did not return an order id");String id=txids.get(0).asText();return new Acknowledged(id,request.executionAttemptId().toString());});
        }catch(BrokerAuthorizationException|InvalidOrderException|InsufficientFundsException e){return new Rejected(null,safeCode(e));}
         catch(BrokerAuthenticationException e){return new Rejected(null,"BROKER_AUTHENTICATION_FAILED");}
         catch(BrokerRateLimitException e){return new Unknown("BROKER_RATE_LIMITED");}
         catch(BrokerUnavailableException e){return new Unknown("PROVIDER_UNAVAILABLE");}
         catch(BrokerProtocolException|UnknownBrokerException e){return new Unknown("BROKER_RESPONSE_UNCERTAIN");}
    }
    public ReconciliationResult reconcile(ReconciliationRequest request){
        try{return sessions.withCredentials(request.brokerAccountId(),c->{List<OrderSnapshot> matches=readOrders(c,clientOrderId(request.idempotencyKey()));
            if(matches.isEmpty())return new ConfirmedAbsent();if(matches.size()>1)return new Inconsistent("MULTIPLE_MATCHING_ORDERS");OrderSnapshot order=matches.get(0);return new ReconciledOrder(order.externalOrderId(),request.executionAttemptId().toString(),order.status());});
        }catch(BrokerTechnicalException e){return new Inconsistent(safeCode(e));}
    }
    private List<OrderSnapshot> readOrders(com.hope.trading.broker_service.credential.domain.CredentialMaterial c,String clientId){
        List<OrderSnapshot> result=new ArrayList<>();collect(client.privatePost("/0/private/OpenOrders",Map.of(),c).path("open"),clientId,result);collect(client.privatePost("/0/private/ClosedOrders",Map.of(),c).path("closed"),clientId,result);return List.copyOf(result);
    }
    private void collect(JsonNode orders,String clientId,List<OrderSnapshot> target){orders.fields().forEachRemaining(e->{OrderSnapshot order=mapper.order(e.getKey(),e.getValue(),clock.instant());if(clientId==null||clientId.equals(order.clientOrderId()))target.add(order);});}
    private String clientOrderId(String key){return UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
    private String safeCode(Exception e){return e.getClass().getSimpleName().replace("Exception","").toUpperCase(Locale.ROOT);}
    private String required(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("externalOrderId is required");return value;}
}
