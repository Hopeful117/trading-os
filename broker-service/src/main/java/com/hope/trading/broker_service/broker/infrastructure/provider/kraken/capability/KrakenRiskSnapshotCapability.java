package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.RiskSnapshotCapability;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerTechnicalException;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import com.hope.trading.broker_service.broker.infrastructure.persistence.RiskSnapshotPersistence;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication.ProviderCredentialSession;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client.KrakenProviderClient;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.mapper.KrakenAssetNormalizer;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public final class KrakenRiskSnapshotCapability implements RiskSnapshotCapability {
    static final int MAX_PAGES = 200;
    static final int MAX_CAPTURE_ATTEMPTS = 3;
    private static final String PROVIDER = "KRAKEN";
    private static final String POSITION_PROVENANCE = "KRAKEN_OPEN_POSITION_TXID";
    private final ProviderCredentialSession sessions;
    private final KrakenProviderClient client;
    private final RiskSnapshotPersistence persistence;
    private final Clock clock;

    public KrakenRiskSnapshotCapability(ProviderCredentialSession sessions, KrakenProviderClient client,
            RiskSnapshotPersistence persistence, Clock clock) {
        this.sessions=sessions;this.client=client;this.persistence=persistence;this.clock=clock;
    }

    @Override
    public RiskSnapshot snapshot(UUID accountId, Instant from, Instant to) {
        Capture capture=null;
        for(int attempt=1;attempt<=MAX_CAPTURE_ATTEMPTS;attempt++) {
            capture=acquire(accountId,from,to);
            if(capture.coherent()||attempt==MAX_CAPTURE_ATTEMPTS)break;
        }
        Draft draft=capture.draft();Instant observedAt=capture.observedAt();
        SnapshotCompleteness completeness=draft.reasons.isEmpty()
                ?SnapshotCompleteness.COMPLETE:SnapshotCompleteness.INCOMPLETE;
        long version=persistence.issueVersion(accountId,observedAt,completeness);
        return new RiskSnapshot(accountId,version,observedAt,completeness,draft.reasons,draft.balances,
                draft.account,draft.positions,draft.closedTrades,draft.ledgerEntries);
    }

    private Capture acquire(UUID accountId,Instant from,Instant to) {
        Draft draft=new Draft();AcquiredFacts facts=new AcquiredFacts(Map.of(),Map.of(),
                new PositionFacts(List.of(),Map.of()),Map.of());
        try {
            facts=sessions.withCredentials(accountId, credentials -> {
                Map<String,BigDecimal> before=read("ASSET_BALANCES_UNAVAILABLE",draft,
                        ()->balances(credentials),Map.of());
                draft.account=read("ACCOUNT_RISK_FACTS_UNAVAILABLE",draft,
                        ()->accountFacts(credentials),new AccountRiskFacts("USD",null,null,null));
                PositionFacts positions=read("POSITIONS_UNAVAILABLE",draft,
                        ()->positions(credentials),new PositionFacts(List.of(),Map.of()));
                Map<String,JsonNode> stops=read("PROTECTIVE_STOPS_UNAVAILABLE",draft,
                        ()->object(client.privatePost("/0/private/OpenOrders",Map.of(),credentials),"open"),Map.of());
                draft.closedTrades=read("CLOSED_TRADES_UNAVAILABLE",draft,
                        ()->closedTrades(credentials,from,to),List.of());
                draft.ledgerEntries=read("LEDGER_ENTRIES_UNAVAILABLE",draft,
                        ()->ledgerEntries(credentials,from,to),List.of());
                Map<String,BigDecimal> after=read("ASSET_BALANCES_UNAVAILABLE",draft,
                        ()->balances(credentials),Map.of());
                return new AcquiredFacts(before,after,positions,stops);
            });
        } catch (BrokerTechnicalException | IllegalArgumentException failure) {
            draft.reasons.add("BROKER_ACCOUNT_UNAVAILABLE");
        }
        Instant observedAt=clock.instant();
        draft.balances=facts.after();
        draft.positions=correlateStops(accountId,facts.positions(),facts.stops(),draft,observedAt);
        if(!draft.reasons.contains("ASSET_BALANCES_UNAVAILABLE")&&!balancesEqual(facts.before(),facts.after()))
            draft.reasons.add("BALANCES_CHANGED_DURING_CAPTURE");
        validateBoundary(draft,observedAt);
        reconcileLedger(draft);
        return new Capture(draft,observedAt,isCoherent(draft));
    }

    private Map<String,BigDecimal> balances(CredentialMaterial credentials) {
        JsonNode result=client.privatePost("/0/private/Balance",Map.of(),credentials);
        if(!result.isObject())throw unavailable();
        Map<String,BigDecimal> values=new TreeMap<>();
        result.fields().forEachRemaining(entry->values.merge(KrakenAssetNormalizer.asset(required(entry.getKey())),
                decimal(entry.getValue()),BigDecimal::add));
        return values;
    }

    private AccountRiskFacts accountFacts(CredentialMaterial credentials) {
        JsonNode result=client.privatePost("/0/private/TradeBalance",Map.of("asset","ZUSD"),credentials);
        return new AccountRiskFacts(KrakenAssetNormalizer.asset("ZUSD"),decimal(result,"eb"),
                decimal(result,"e"),decimal(result,"m"));
    }

    private PositionFacts positions(CredentialMaterial credentials) {
        Map<String,JsonNode> raw=object(client.privatePost("/0/private/OpenPositions",Map.of(),credentials),null);
        List<RawPosition> positions=new ArrayList<>();
        Map<String,Set<Integer>> references=new HashMap<>();
        for(var entry:raw.entrySet()) {
            JsonNode value=entry.getValue();String providerRef=required(entry.getKey());
            BigDecimal volume=decimal(value,"vol");BigDecimal closed=optionalDecimal(value,"vol_closed",BigDecimal.ZERO);
            BigDecimal quantity=volume.subtract(closed);if(quantity.signum()<=0)throw unavailable();
            if("sell".equals(required(value.path("type").asText(null))))quantity=quantity.negate();
            else if(!"buy".equals(value.path("type").asText()))throw unavailable();
            BigDecimal entryPrice=decimal(value,"cost").divide(volume,MathContext.DECIMAL128);
            RawPosition position=new RawPosition(providerRef,
                    KrakenAssetNormalizer.pair(required(value.path("pair").asText(null))).instrument(),
                    quantity,entryPrice,decimal(value,"cost"),decimal(value,"value"),
                    decimal(value,"net"),decimal(value,"margin"));
            int index=positions.size();positions.add(position);
            references.computeIfAbsent(providerRef,ignored->new HashSet<>()).add(index);
            String openingOrder=value.path("ordertxid").asText(null);
            if(openingOrder!=null&&!openingOrder.isBlank())references.computeIfAbsent(openingOrder,ignored->new HashSet<>()).add(index);
        }
        return new PositionFacts(List.copyOf(positions),references);
    }

    private List<RiskPosition> correlateStops(UUID accountId,PositionFacts facts,Map<String,JsonNode> orders,
            Draft draft,Instant observedAt) {
        BigDecimal[] protectedQuantities=new BigDecimal[facts.positions.size()];
        Arrays.fill(protectedQuantities,BigDecimal.ZERO);
        List<List<ProtectiveStop>> correlatedStops=new ArrayList<>();
        for(int i=0;i<facts.positions.size();i++)correlatedStops.add(new ArrayList<>());
        for(var orderEntry:orders.entrySet()) {
            JsonNode order=orderEntry.getValue();
            JsonNode description=order.path("descr");String orderType=description.path("ordertype").asText("");
            String status=order.path("status").asText("open");
            if(!("open".equals(status)||"pending".equals(status))||!orderType.startsWith("stop-loss"))continue;
            String reference=order.path("refid").asText(null);
            Set<Integer> matches=reference==null?Set.of():facts.references.getOrDefault(reference,Set.of());
            if(matches.size()!=1){draft.reasons.add("AMBIGUOUS_PROTECTIVE_STOP");continue;}
            int index=matches.iterator().next();RawPosition position=facts.positions.get(index);
            String stopSide=description.path("type").asText(null);String stopInstrument;
            try { stopInstrument=KrakenAssetNormalizer.pair(description.path("pair").asText(null)).instrument(); }
            catch(BrokerTechnicalException invalidPair) { draft.reasons.add("AMBIGUOUS_PROTECTIVE_STOP");continue; }
            if(!position.instrument.equals(stopInstrument)||!(position.quantity.signum()>0?"sell":"buy").equals(stopSide)) {
                draft.reasons.add("AMBIGUOUS_PROTECTIVE_STOP");continue;
            }
            BigDecimal remaining=decimal(order,"vol").subtract(optionalDecimal(order,"vol_exec",BigDecimal.ZERO));
            if(remaining.signum()<=0){draft.reasons.add("AMBIGUOUS_PROTECTIVE_STOP");continue;}
            protectedQuantities[index]=protectedQuantities[index].add(remaining);
            correlatedStops.get(index).add(new ProtectiveStop(required(orderEntry.getKey()),
                    "KRAKEN_OPEN_ORDER_TXID",remaining,decimal(description,"price")));
        }
        List<RiskPosition> result=new ArrayList<>();
        for(int i=0;i<facts.positions.size();i++) {
            RawPosition p=facts.positions.get(i);BigDecimal protectedQuantity=protectedQuantities[i];
            if(protectedQuantity.signum()>0&&protectedQuantity.compareTo(p.quantity.abs())!=0)
                draft.reasons.add("PARTIAL_PROTECTIVE_STOP");
            UUID id=persistence.positionId(accountId,PROVIDER,p.providerReference,POSITION_PROVENANCE,observedAt);
            result.add(new RiskPosition(id,p.providerReference,POSITION_PROVENANCE,p.instrument,
                    p.quantity,p.entryPrice,p.cost,p.marketValue,p.unrealizedPnl,p.margin,
                    protectedQuantity,correlatedStops.get(i)));
        }
        return List.copyOf(result);
    }

    private List<ClosedTrade> closedTrades(CredentialMaterial credentials,Instant from,Instant to) {
        Map<String,JsonNode> trades=pages("/0/private/TradesHistory","trades",credentials,from,to,
                Map.of("type","closed position","trades","true"));
        List<ClosedTrade> result=new ArrayList<>();
        for(var entry:trades.entrySet()) {
            JsonNode value=entry.getValue();Instant closedAt=instant(value,"closetm");
            if(closedAt.isBefore(from)||!closedAt.isBefore(to))continue;
            String type=required(value.path("type").asText(null));Side side=switch(type){case "buy"->Side.BUY;case "sell"->Side.SELL;default->throw unavailable();};
            KrakenAssetNormalizer.Pair pair=KrakenAssetNormalizer.pair(value.path("pair").asText(null));
            result.add(new ClosedTrade(required(entry.getKey()),pair.instrument(),pair.quoteAsset(),side,
                    decimal(value,"cvol"),decimal(value,"cprice"),decimal(value,"cfee"),decimal(value,"net"),closedAt));
        }
        return List.copyOf(result);
    }

    private List<LedgerEntry> ledgerEntries(CredentialMaterial credentials,Instant from,Instant to) {
        Map<String,JsonNode> ledger=pages("/0/private/Ledgers","ledger",credentials,from,to,Map.of());
        List<LedgerEntry> result=new ArrayList<>();
        for(var entry:ledger.entrySet()) {
            JsonNode value=entry.getValue();Instant occurredAt=instant(value,"time");
            if(occurredAt.isBefore(from)||!occurredAt.isBefore(to))continue;
            result.add(new LedgerEntry(required(entry.getKey()),KrakenAssetNormalizer.asset(value.path("asset").asText(null)),
                    required(value.path("type").asText(null)),decimal(value,"amount"),decimal(value,"fee"),
                    decimal(value,"balance"),occurredAt));
        }
        return List.copyOf(result);
    }

    private Map<String,JsonNode> pages(String path,String collection,CredentialMaterial credentials,
            Instant from,Instant to,Map<String,String> fixed) {
        Map<String,JsonNode> all=new LinkedHashMap<>();Integer expected=null;int offset=0;
        for(int page=0;page<MAX_PAGES;page++) {
            Map<String,String> parameters=new LinkedHashMap<>(fixed);
            parameters.put("start",Long.toString(from.getEpochSecond()));
            parameters.put("end",Long.toString(to.getEpochSecond()+(to.getNano()==0?0:1)));
            parameters.put("ofs",Integer.toString(offset));
            JsonNode response=client.privatePost(path,parameters,credentials);
            if(!response.path("count").canConvertToInt())throw unavailable();
            int count=response.path("count").intValue();if(expected==null)expected=count;else if(expected!=count)throw unavailable();
            Map<String,JsonNode> entries=object(response,collection);
            for(var entry:entries.entrySet())if(all.putIfAbsent(entry.getKey(),entry.getValue())!=null)throw unavailable();
            if(all.size()==expected)return all;
            if(entries.isEmpty()||all.size()>expected)throw unavailable();
            offset+=entries.size();
        }
        throw unavailable();
    }

    private static void validateBoundary(Draft draft,Instant observedAt) {
        if(draft.closedTrades.stream().anyMatch(trade->trade.closedAt().isAfter(observedAt))
                ||draft.ledgerEntries.stream().anyMatch(entry->entry.occurredAt().isAfter(observedAt)))
            draft.reasons.add("FACT_AFTER_OBSERVATION_BOUNDARY");
    }

    private static void reconcileLedger(Draft draft) {
        if(draft.reasons.contains("LEDGER_ENTRIES_UNAVAILABLE")
                ||draft.reasons.contains("ASSET_BALANCES_UNAVAILABLE"))return;
        Map<String,List<LedgerEntry>> byAsset=new TreeMap<>();
        for(LedgerEntry entry:draft.ledgerEntries)
            byAsset.computeIfAbsent(entry.asset(),ignored->new ArrayList<>()).add(entry);
        for(var assetEntries:byAsset.entrySet()) {
            List<LedgerEntry> entries=assetEntries.getValue();
            entries.sort(Comparator.comparing(LedgerEntry::occurredAt)
                    .thenComparing(LedgerEntry::providerLedgerReference));
            for(int i=0;i<entries.size();i++) {
                LedgerEntry entry=entries.get(i);
                if(entry.fee().signum()<0){draft.reasons.add("LEDGER_FEE_INVALID");break;}
                if(i>0) {
                    // Kraken ledger amount excludes its separately reported fee.
                    BigDecimal expected=entries.get(i-1).balance().add(entry.amount()).subtract(entry.fee());
                    if(expected.compareTo(entry.balance())!=0){draft.reasons.add("LEDGER_RUNNING_BALANCE_MISMATCH");break;}
                }
            }
            BigDecimal current=draft.balances.get(assetEntries.getKey());
            if(current==null||current.compareTo(entries.get(entries.size()-1).balance())!=0)
                draft.reasons.add("LEDGER_TERMINAL_BALANCE_MISMATCH");
        }
    }

    private static boolean balancesEqual(Map<String,BigDecimal> left,Map<String,BigDecimal> right) {
        if(!left.keySet().equals(right.keySet()))return false;
        return left.entrySet().stream()
                .allMatch(entry->entry.getValue().compareTo(right.get(entry.getKey()))==0);
    }

    private static boolean isCoherent(Draft draft) {
        return draft.reasons.stream().noneMatch(reason->reason.equals("BALANCES_CHANGED_DURING_CAPTURE")
                ||reason.equals("FACT_AFTER_OBSERVATION_BOUNDARY")||reason.startsWith("LEDGER_"));
    }

    private static Map<String,JsonNode> object(JsonNode parent,String field) {
        JsonNode node=field==null?parent:parent.path(field);if(!node.isObject())throw unavailable();
        Map<String,JsonNode> result=new LinkedHashMap<>();node.fields().forEachRemaining(e->result.put(e.getKey(),e.getValue()));return result;
    }
    private static BigDecimal decimal(JsonNode parent,String field){return decimal(parent.path(field));}
    private static BigDecimal decimal(JsonNode node){if(node.isMissingNode()||node.isNull()||!node.isValueNode())throw unavailable();try{return new BigDecimal(node.asText());}catch(NumberFormatException e){throw unavailable();}}
    private static BigDecimal optionalDecimal(JsonNode parent,String field,BigDecimal fallback){JsonNode node=parent.path(field);return node.isMissingNode()||node.isNull()?fallback:decimal(node);}
    private static Instant instant(JsonNode parent,String field){BigDecimal seconds=decimal(parent,field);long whole=seconds.longValue();return Instant.ofEpochSecond(whole,seconds.subtract(BigDecimal.valueOf(whole)).movePointRight(9).longValue());}
    private static String required(String value){if(value==null||value.isBlank())throw unavailable();return value;}
    private static FactUnavailable unavailable(){return new FactUnavailable();}
    private static <T>T read(String reason,Draft draft,Supplier<T> operation,T fallback){try{return operation.get();}catch(BrokerTechnicalException|FactUnavailable|ArithmeticException e){draft.reasons.add(reason);return fallback;}}

    private static final class Draft {
        final List<String> reasons=new ArrayList<>();Map<String,BigDecimal> balances=Map.of();
        AccountRiskFacts account=new AccountRiskFacts("USD",null,null,null);List<RiskPosition> positions=List.of();
        List<ClosedTrade> closedTrades=List.of();List<LedgerEntry> ledgerEntries=List.of();
    }
    private record RawPosition(String providerReference,String instrument,BigDecimal quantity,
            BigDecimal entryPrice,BigDecimal cost,BigDecimal marketValue,BigDecimal unrealizedPnl,
            BigDecimal margin) {}
    private record PositionFacts(List<RawPosition> positions,Map<String,Set<Integer>> references) {}
    private record AcquiredFacts(Map<String,BigDecimal> before,Map<String,BigDecimal> after,
            PositionFacts positions,Map<String,JsonNode> stops) {}
    private record Capture(Draft draft,Instant observedAt,boolean coherent) {}
    private static final class FactUnavailable extends RuntimeException {}
}
