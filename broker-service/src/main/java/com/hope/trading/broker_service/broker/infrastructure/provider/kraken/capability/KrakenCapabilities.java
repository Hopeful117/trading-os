package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.*;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.*;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication.ProviderCredentialSession;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client.KrakenProviderClient;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.mapper.KrakenOrderMapper;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.mapper.KrakenAssetNormalizer;
import com.hope.trading.broker_service.kraken.config.KrakenProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public final class KrakenCapabilities implements AuthenticationCapability,AccountCapability,
        PositionCapability,OrderCapability,ExecutionCapability,ReconciliationCapability,
        TechnicalCapability,MarginCapability {
    private static final String PROVIDER = "KRAKEN";
    private static final String ASSET_PAIRS_PATH = "/0/public/AssetPairs";
    private static final String MARGIN_SOURCE = "KRAKEN_ASSET_PAIRS_MARGIN";
    private static final BigDecimal CASH_LEVERAGE = BigDecimal.ONE;
    private final ProviderCredentialSession sessions;
    private final KrakenProviderClient client;
    private final KrakenOrderMapper mapper;
    private final Clock clock;
    private final KrakenProperties properties;
    public KrakenCapabilities(ProviderCredentialSession sessions,KrakenProviderClient client,KrakenOrderMapper mapper,Clock clock){this(sessions,client,mapper,clock,new KrakenProperties());}
    @Autowired
    public KrakenCapabilities(ProviderCredentialSession sessions,KrakenProviderClient client,KrakenOrderMapper mapper,Clock clock,KrakenProperties properties){this.sessions=sessions;this.client=client;this.mapper=mapper;this.clock=clock;this.properties=properties;}
    public void verify(UUID accountId){sessions.withCredentials(accountId,c->{client.privatePost("/0/private/Balance",Map.of(),c);return null;});}
    public AccountSnapshot account(UUID accountId){return sessions.withCredentials(accountId,c->{
        JsonNode result=client.privatePost("/0/private/Balance",Map.of(),c);Map<String,BigDecimal> balances=new TreeMap<>();
        result.fields().forEachRemaining(entry->balances.merge(KrakenAssetNormalizer.asset(entry.getKey()),
                new BigDecimal(entry.getValue().asText("0")),BigDecimal::add));
        return new AccountSnapshot(accountId,balances,clock.instant());});}
    public List<PositionSnapshot> positions(UUID accountId){return sessions.withCredentials(accountId,c->{
        JsonNode result=client.privatePost("/0/private/OpenPositions",Map.of(),c);List<PositionSnapshot> positions=new ArrayList<>();
        result.fields().forEachRemaining(entry->{String txid=entry.getKey();JsonNode p=entry.getValue();BigDecimal quantity=new BigDecimal(p.path("vol").asText("0"));if("sell".equals(p.path("type").asText()))quantity=quantity.negate();positions.add(new PositionSnapshot(KrakenAssetNormalizer.pair(p.path("pair").asText()).instrument(),quantity,new BigDecimal(p.path("cost").asText("0")).divide(new BigDecimal(p.path("vol").asText("1")),java.math.MathContext.DECIMAL64),clock.instant(),txid));});
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
    @Override public TechnicalCapabilities capabilities(UUID accountId,String instrument) {
          MarginFacts facts = marginFacts(instrument);
          if (instrument == null || (!properties.getSupportedInstruments().isEmpty() && properties.getSupportedInstruments().stream()
                  .noneMatch(value -> value.equalsIgnoreCase(instrument))))
              throw new BrokerTechnicalException("Instrument capability is unavailable");
          return new TechnicalCapabilities(accountId,PROVIDER,instrument,properties.getCapabilityVersion(),clock.instant(),
                  List.of(OrderType.MARKET,OrderType.LIMIT),List.copyOf(facts.buy()),List.copyOf(facts.sell()));
     }
    @Override public MarginPreview preview(MarginPreviewRequest request) {
          MarginFacts facts = marginFacts(request.instrument());
          BigDecimal leverage = request.leverage() == null ? CASH_LEVERAGE : request.leverage();
          if (leverage.compareTo(CASH_LEVERAGE) > 0 && !facts.leverageFor(request.side()).contains(leverage))
              throw new BrokerTechnicalException("Requested leverage is unavailable for instrument and side");
          BigDecimal amount = request.quantity().multiply(request.price()).divide(leverage, java.math.MathContext.DECIMAL64);
          return new MarginPreview(request.brokerAccountId(),request.instrument(),amount,facts.quoteCurrency(),MARGIN_SOURCE,
                  properties.getCapabilityVersion(),clock.instant());
     }
     private MarginFacts marginFacts(String instrument) {
          if (instrument == null || instrument.isBlank() || (!properties.getSupportedInstruments().isEmpty() && properties.getSupportedInstruments().stream()
                  .noneMatch(value -> value.equalsIgnoreCase(instrument))))
              throw new BrokerTechnicalException("Instrument capability is unavailable");
          JsonNode result = client.publicGet(ASSET_PAIRS_PATH,Map.of("pair",instrument,"info","info","assetVersion","1"));
          JsonNode pair = findPair(result,instrument);
          if (pair == null) throw new BrokerTechnicalException("Instrument capability is unavailable");
          String quote = pair.path("quote").asText("").trim();
          if (quote.isBlank()) throw new BrokerTechnicalException("Margin currency is unavailable");
          Set<BigDecimal> buy = leverageLevels(pair.path("leverage_buy"));
          Set<BigDecimal> sell = leverageLevels(pair.path("leverage_sell"));
          buy.add(CASH_LEVERAGE); sell.add(CASH_LEVERAGE);
          return new MarginFacts(KrakenAssetNormalizer.asset(quote),buy,sell);
     }
     private JsonNode findPair(JsonNode result,String instrument) {
          Iterator<JsonNode> pairs=result.elements();
          while(pairs.hasNext()) {
              JsonNode pair=pairs.next();
              String name=pair.path("wsname").asText(pair.path("altname").asText(""));
              String canonical=pair.path("base").asText("")+"/"+pair.path("quote").asText("");
              if(instrument.equalsIgnoreCase(name)||instrument.equalsIgnoreCase(pair.path("altname").asText("") )
                      || instrument.equalsIgnoreCase(canonical)) return pair;
          }
          JsonNode direct=result.path(instrument);return direct.isObject()?direct:null;
     }
     private Set<BigDecimal> leverageLevels(JsonNode values) {Set<BigDecimal> levels=new TreeSet<>();if(values.isArray())values.elements().forEachRemaining(value->{if(value.isNumber()&&value.decimalValue().signum()>0)levels.add(value.decimalValue());});return levels;}
     private record MarginFacts(String quoteCurrency,Set<BigDecimal> buy,Set<BigDecimal> sell) {
          Set<BigDecimal> leverageFor(Side side){return side==Side.BUY?buy:sell;}
     }
    private List<OrderSnapshot> readOrders(com.hope.trading.broker_service.credential.domain.CredentialMaterial c,String clientId){
        List<OrderSnapshot> result=new ArrayList<>();collect(client.privatePost("/0/private/OpenOrders",Map.of(),c).path("open"),clientId,result);collect(client.privatePost("/0/private/ClosedOrders",Map.of(),c).path("closed"),clientId,result);return List.copyOf(result);
    }
    private void collect(JsonNode orders,String clientId,List<OrderSnapshot> target){orders.fields().forEachRemaining(e->{OrderSnapshot order=mapper.order(e.getKey(),e.getValue(),clock.instant());if(clientId==null||clientId.equals(order.clientOrderId()))target.add(order);});}
    private String clientOrderId(String key){return UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
    private String safeCode(Exception e){return e.getClass().getSimpleName().replace("Exception","").toUpperCase(Locale.ROOT);}
    private String required(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("externalOrderId is required");return value;}
}
