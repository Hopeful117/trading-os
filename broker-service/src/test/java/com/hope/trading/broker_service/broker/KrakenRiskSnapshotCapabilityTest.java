package com.hope.trading.broker_service.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import com.hope.trading.broker_service.broker.infrastructure.persistence.RiskSnapshotPersistence;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication.ProviderCredentialSession;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.capability.KrakenRiskSnapshotCapability;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client.KrakenProviderClient;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class KrakenRiskSnapshotCapabilityTest {
    private static final Instant FROM=Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant TO=Instant.parse("2026-08-02T00:00:00Z");
    private static final Instant NOW=Instant.parse("2026-08-02T01:00:00Z");
    private final ObjectMapper json=new ObjectMapper();

    @Test
    void scopesProviderAndPersistenceReadsToRequestedAccountAndCorrelatesCompleteStop() {
        UUID account=UUID.randomUUID();UUID positionId=UUID.randomUUID();
        CapturingSession session=new CapturingSession();StubClient client=new StubClient(json);
        RiskSnapshotPersistence persistence=mock(RiskSnapshotPersistence.class);
        when(persistence.positionId(eq(account),eq("KRAKEN"),eq("POSITION-1"),anyString(),eq(NOW))).thenReturn(positionId);
        when(persistence.issueVersion(eq(account),eq(NOW),eq(SnapshotCompleteness.COMPLETE))).thenReturn(41L);

        RiskSnapshot snapshot=capability(session,client,persistence).snapshot(account,FROM,TO);

        assertThat(session.accounts).containsOnly(account);
        assertThat(snapshot.brokerAccountId()).isEqualTo(account);
        assertThat(snapshot.completeness()).as(snapshot.unavailabilityReasons().toString())
                .isEqualTo(SnapshotCompleteness.COMPLETE);
        assertThat(snapshot.snapshotVersion()).isEqualTo(41);
        assertThat(snapshot.assetBalances()).containsEntry("BTC",new java.math.BigDecimal("2"))
                .containsEntry("USD",new java.math.BigDecimal("100"));
        assertThat(snapshot.account().valuationAsset()).isEqualTo("USD");
        assertThat(snapshot.positions()).singleElement().satisfies(position->{
            assertThat(position.positionId()).isEqualTo(positionId);
            assertThat(position.providerPositionReference()).isEqualTo("POSITION-1");
            assertThat(position.providerReferenceProvenance()).isEqualTo("KRAKEN_OPEN_POSITION_TXID");
            assertThat(position.instrument()).isEqualTo("BTC/USD");
            assertThat(position.protectedQuantity()).isEqualByComparingTo("2");
            assertThat(position.marketValue()).isEqualByComparingTo("120");
            assertThat(position.margin()).isEqualByComparingTo("20");
            assertThat(position.protectiveStops()).singleElement().satisfies(stop->{
                assertThat(stop.providerOrderReference()).isEqualTo("STOP-1");
                assertThat(stop.stopPrice()).isEqualByComparingTo("45");
            });
        });
    }

    @Test
    void marksPartialProtectiveStopIncomplete() {
        StubClient client=new StubClient(json);client.stopVolume="1";
        RiskSnapshot snapshot=capability(new CapturingSession(),client,persistence()).snapshot(UUID.randomUUID(),FROM,TO);
        assertThat(snapshot.completeness()).isEqualTo(SnapshotCompleteness.INCOMPLETE);
        assertThat(snapshot.unavailabilityReasons()).contains("PARTIAL_PROTECTIVE_STOP");
    }

    @Test
    void marksAmbiguousStopAndMalformedAuthoritativeFactsIncompleteWithoutFabricatingValues() {
        StubClient client=new StubClient(json);client.stopReference=null;client.malformedTradeBalance=true;
        RiskSnapshot snapshot=capability(new CapturingSession(),client,persistence()).snapshot(UUID.randomUUID(),FROM,TO);
        assertThat(snapshot.completeness()).isEqualTo(SnapshotCompleteness.INCOMPLETE);
        assertThat(snapshot.unavailabilityReasons()).contains("AMBIGUOUS_PROTECTIVE_STOP",
                "ACCOUNT_RISK_FACTS_UNAVAILABLE");
        assertThat(snapshot.account().equity()).isNull();
    }

    @Test
    void failsClosedWhenPaginationCannotProveCompleteness() {
        StubClient client=new StubClient(json);client.incompleteTradesPage=true;
        RiskSnapshot snapshot=capability(new CapturingSession(),client,persistence()).snapshot(UUID.randomUUID(),FROM,TO);
        assertThat(snapshot.completeness()).isEqualTo(SnapshotCompleteness.INCOMPLETE);
        assertThat(snapshot.unavailabilityReasons()).contains("CLOSED_TRADES_UNAVAILABLE");
        assertThat(snapshot.closedTrades()).isEmpty();
    }

    @Test
    void filtersClosedTradesAndLedgerEntriesByExactHalfOpenInterval() {
        StubClient client=new StubClient(json);client.withIntervalEntries=true;
        RiskSnapshot snapshot=capability(new CapturingSession(),client,persistence()).snapshot(UUID.randomUUID(),FROM,TO);
        assertThat(snapshot.closedTrades()).extracting(ClosedTrade::providerTradeReference).containsExactly("TRADE-IN");
        assertThat(snapshot.ledgerEntries()).extracting(LedgerEntry::providerLedgerReference).containsExactly("LEDGER-IN");
        assertThat(snapshot.closedTrades()).singleElement().satisfies(trade->{
            assertThat(trade.instrument()).isEqualTo("BTC/USD");
            assertThat(trade.settlementAsset()).isEqualTo("USD");
        });
        assertThat(snapshot.ledgerEntries()).singleElement().satisfies(entry->assertThat(entry.asset()).isEqualTo("USD"));
        assertThat(json.findAndRegisterModules().valueToTree(snapshot).toString())
                .doesNotContain("ZUSD","XXBT","XBTUSD","XXBTZUSD")
                .contains("\"settlementAsset\":\"USD\"");
    }

    @Test
    void unknownClosedTradePairMakesFactsIncompleteInsteadOfAssumingSettlementAsset() {
        StubClient client=new StubClient(json);client.withIntervalEntries=true;client.unsupportedTradePair=true;
        RiskSnapshot snapshot=capability(new CapturingSession(),client,persistence()).snapshot(UUID.randomUUID(),FROM,TO);
        assertThat(snapshot.completeness()).isEqualTo(SnapshotCompleteness.INCOMPLETE);
        assertThat(snapshot.unavailabilityReasons()).contains("CLOSED_TRADES_UNAVAILABLE");
        assertThat(snapshot.closedTrades()).isEmpty();
    }

    @Test
    void retriesActivityBetweenBalanceCallsAndCompletesOnlyStableCapture() {
        StubClient client=new StubClient(json);
        client.balanceSequence.addAll(List.of("100","101","101","101"));

        RiskSnapshot snapshot=capability(new CapturingSession(),client,persistence()).snapshot(UUID.randomUUID(),FROM,TO);

        assertThat(snapshot.completeness()).isEqualTo(SnapshotCompleteness.COMPLETE);
        assertThat(snapshot.assetBalances()).containsEntry("USD",new java.math.BigDecimal("101"));
        assertThat(client.balanceReads).isEqualTo(4);
    }

    @Test
    void marksCaptureIncompleteAfterBoundedActivityRetries() {
        StubClient client=new StubClient(json);client.changeEveryBalance=true;

        RiskSnapshot snapshot=capability(new CapturingSession(),client,persistence()).snapshot(UUID.randomUUID(),FROM,TO);

        assertThat(snapshot.completeness()).isEqualTo(SnapshotCompleteness.INCOMPLETE);
        assertThat(snapshot.unavailabilityReasons()).contains("BALANCES_CHANGED_DURING_CAPTURE");
        assertThat(client.balanceReads).isEqualTo(6);
    }

    @Test
    void rejectsRunningLedgerMismatch() {
        StubClient client=new StubClient(json);client.balanceUsd="86";
        client.ledgerJson="{\"count\":2,\"ledger\":{"+
                "\"L1\":{\"asset\":\"ZUSD\",\"type\":\"trade\",\"amount\":\"10\",\"fee\":\"1\",\"balance\":\"90\",\"time\":"+(FROM.getEpochSecond()+1)+"},"+
                "\"L2\":{\"asset\":\"ZUSD\",\"type\":\"trade\",\"amount\":\"-4\",\"fee\":\"1\",\"balance\":\"86\",\"time\":"+(FROM.getEpochSecond()+2)+"}}}";

        RiskSnapshot snapshot=capability(new CapturingSession(),client,persistence()).snapshot(UUID.randomUUID(),FROM,TO);

        assertThat(snapshot.completeness()).isEqualTo(SnapshotCompleteness.INCOMPLETE);
        assertThat(snapshot.unavailabilityReasons()).contains("LEDGER_RUNNING_BALANCE_MISMATCH");
    }

    @Test
    void rejectsTerminalLedgerMismatchWithCurrentAssetBalance() {
        StubClient client=new StubClient(json);client.balanceUsd="100";
        client.ledgerJson="{\"count\":1,\"ledger\":{\"L1\":{\"asset\":\"ZUSD\",\"type\":\"trade\",\"amount\":\"10\",\"fee\":\"1\",\"balance\":\"99\",\"time\":"+(FROM.getEpochSecond()+1)+"}}}";

        RiskSnapshot snapshot=capability(new CapturingSession(),client,persistence()).snapshot(UUID.randomUUID(),FROM,TO);

        assertThat(snapshot.completeness()).isEqualTo(SnapshotCompleteness.INCOMPLETE);
        assertThat(snapshot.unavailabilityReasons()).contains("LEDGER_TERMINAL_BALANCE_MISMATCH");
    }

    @Test
    void appliesAmountMinusFeeSemanticsToRunningLedgerBalances() {
        StubClient client=new StubClient(json);client.balanceUsd="85";
        client.ledgerJson="{\"count\":2,\"ledger\":{"+
                "\"L1\":{\"asset\":\"ZUSD\",\"type\":\"trade\",\"amount\":\"10\",\"fee\":\"1\",\"balance\":\"90\",\"time\":"+(FROM.getEpochSecond()+1)+"},"+
                "\"L2\":{\"asset\":\"ZUSD\",\"type\":\"trade\",\"amount\":\"-4\",\"fee\":\"1\",\"balance\":\"85\",\"time\":"+(FROM.getEpochSecond()+2)+"}}}";

        RiskSnapshot snapshot=capability(new CapturingSession(),client,persistence()).snapshot(UUID.randomUUID(),FROM,TO);

        assertThat(snapshot.completeness()).isEqualTo(SnapshotCompleteness.COMPLETE);
    }

    @Test
    void rejectsFactsAfterCompletedAcquisitionBoundary() {
        StubClient client=new StubClient(json);client.futureTrade=true;

        RiskSnapshot snapshot=capability(new CapturingSession(),client,persistence()).snapshot(UUID.randomUUID(),
                FROM,NOW.plusSeconds(3600));

        assertThat(snapshot.completeness()).isEqualTo(SnapshotCompleteness.INCOMPLETE);
        assertThat(snapshot.unavailabilityReasons()).contains("FACT_AFTER_OBSERVATION_BOUNDARY");
        assertThat(snapshot.observedAt()).isEqualTo(NOW);
    }

    private RiskSnapshotPersistence persistence() {
        RiskSnapshotPersistence persistence=mock(RiskSnapshotPersistence.class);
        when(persistence.positionId(any(),anyString(),anyString(),anyString(),any())).thenReturn(UUID.randomUUID());
        when(persistence.issueVersion(any(),any(),any())).thenReturn(1L);return persistence;
    }
    private KrakenRiskSnapshotCapability capability(ProviderCredentialSession session,KrakenProviderClient client,
            RiskSnapshotPersistence persistence) {
        return new KrakenRiskSnapshotCapability(session,client,persistence,Clock.fixed(NOW,ZoneOffset.UTC));
    }

    static final class CapturingSession implements ProviderCredentialSession {
        final List<UUID> accounts=new ArrayList<>();
        public <T>T withCredentials(UUID accountId,Function<CredentialMaterial,T> operation) {
            accounts.add(accountId);try(var credentials=new CredentialMaterial("12345678".toCharArray(),
                    "MTIzNDU2Nzg5MDEyMzQ1Ng==".toCharArray(),null)){return operation.apply(credentials);}
        }
    }

    static final class StubClient implements KrakenProviderClient {
        final ObjectMapper json;String stopVolume="2";String stopReference="OPENING-1";
        boolean malformedTradeBalance;boolean incompleteTradesPage;boolean withIntervalEntries;
        boolean unsupportedTradePair;boolean changeEveryBalance;boolean futureTrade;int balanceReads;
        String balanceUsd="100";String ledgerJson;final Deque<String> balanceSequence=new ArrayDeque<>();
        StubClient(ObjectMapper json){this.json=json;}
        public JsonNode privatePost(String path,Map<String,String> parameters,CredentialMaterial ignored) {
            if(path.endsWith("TradeBalance"))return malformedTradeBalance
                    ?node("{\"eb\":\"100\",\"m\":\"20\"}"):node("{\"eb\":\"100\",\"e\":\"110\",\"m\":\"20\"}");
            if(path.endsWith("Balance")){balanceReads++;String usd=changeEveryBalance
                    ?Integer.toString(100+balanceReads):balanceSequence.isEmpty()?balanceUsd:balanceSequence.removeFirst();
                return node("{\"XXBT\":\"2\",\"ZUSD\":\""+usd+"\"}");}
            if(path.endsWith("OpenPositions"))return node("{\"POSITION-1\":{\"ordertxid\":\"OPENING-1\",\"pair\":\"XXBTZUSD\",\"type\":\"buy\",\"vol\":\"2\",\"vol_closed\":\"0\",\"cost\":\"100\",\"value\":\"120\",\"net\":\"20\",\"margin\":\"20\"}}");
            if(path.endsWith("OpenOrders"))return node("{\"open\":{\"STOP-1\":{\"refid\":"+(stopReference==null?"null":"\""+stopReference+"\"")+",\"status\":\"open\",\"vol\":\""+stopVolume+"\",\"vol_exec\":\"0\",\"descr\":{\"pair\":\"XXBTZUSD\",\"type\":\"sell\",\"ordertype\":\"stop-loss\",\"price\":\"45\"}}}}");
            if(path.endsWith("TradesHistory")) {
                if(incompleteTradesPage)return node("{\"count\":1,\"trades\":{}}");
                if(futureTrade)return node("{\"count\":1,\"trades\":{\"TRADE-FUTURE\":{\"pair\":\"XXBTZUSD\",\"type\":\"sell\",\"cvol\":\"1\",\"cprice\":\"60\",\"cfee\":\"1\",\"net\":\"9\",\"closetm\":"+(NOW.getEpochSecond()+1)+"}}}");
                if(withIntervalEntries){String pair=unsupportedTradePair?"UNKNOWNZUSD":"XXBTZUSD";return node("{\"count\":2,\"trades\":{\"TRADE-IN\":{\"pair\":\""+pair+"\",\"type\":\"sell\",\"cvol\":\"1\",\"cprice\":\"60\",\"cfee\":\"1\",\"net\":\"9\",\"closetm\":"+(FROM.getEpochSecond()+1)+"},\"TRADE-END\":{\"pair\":\""+pair+"\",\"type\":\"sell\",\"cvol\":\"1\",\"cprice\":\"60\",\"cfee\":\"1\",\"net\":\"9\",\"closetm\":"+TO.getEpochSecond()+"}}}");}
                return node("{\"count\":0,\"trades\":{}}");
            }
            if(path.endsWith("Ledgers")) {
                if(ledgerJson!=null)return node(ledgerJson);
                if(withIntervalEntries)return node("{\"count\":2,\"ledger\":{\"LEDGER-IN\":{\"asset\":\"ZUSD\",\"type\":\"trade\",\"amount\":\"10\",\"fee\":\"1\",\"balance\":\"100\",\"time\":"+(FROM.getEpochSecond()+1)+"},\"LEDGER-END\":{\"asset\":\"ZUSD\",\"type\":\"trade\",\"amount\":\"10\",\"fee\":\"1\",\"balance\":\"100\",\"time\":"+TO.getEpochSecond()+"}}}");
                return node("{\"count\":0,\"ledger\":{}}");
            }
            throw new AssertionError(path);
        }
        private JsonNode node(String value){try{return json.readTree(value);}catch(Exception e){throw new RuntimeException(e);}}
    }
}
