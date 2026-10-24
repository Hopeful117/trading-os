package com.hope.trading.trading_core.risk.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.broker.apiClient.BrokerAccountSnapshotV2Client;
import com.hope.trading.trading_core.broker.dto.BrokerAccountFactsV2Dto;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.risk.application.port.BrokerRiskFactsPort;
import com.hope.trading.trading_core.risk.application.port.RiskFactsProvider;
import com.hope.trading.trading_core.model.AccountValuationStatus;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import com.hope.trading.trading_core.valuation.application.PaperDerivativeAccountValuationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import feign.Request;
import feign.RetryableException;

class ModeAwareRiskFactsProviderTest {
    private final BrokerRiskFactsPort liveFacts = mock(BrokerRiskFactsPort.class);
    private final BrokerAccount brokerAccount = mock(BrokerAccount.class);
    private final Instant from = Instant.parse("2026-08-01T00:00:00Z");
    private final Instant to = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void liveUsesFreshAuthoritativeV2FactsForRisk() {
        UUID brokerId = UUID.randomUUID();
        BrokerAccountSnapshotV2Client authoritative = mock(BrokerAccountSnapshotV2Client.class);
        UUID providerReference = UUID.randomUUID();
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.LIVE);
        when(brokerAccount.providerRegistrationReference()).thenReturn(providerReference);
        when(brokerAccount.externalAccountId()).thenReturn("ctrader-1");
        when(authoritative.getAccountSnapshot(brokerId)).thenReturn(new BrokerAccountSnapshotV2Dto(
                brokerId, providerReference, "ctrader-1", Map.of("USD", new BigDecimal("1000")), List.of(),
                from.plusSeconds(1), new BrokerAccountFactsV2Dto("USD", new BigDecimal("1000"),
                new BigDecimal("990"), new BigDecimal("10"), new BigDecimal("980")), List.of(), List.of(),
                "COMPLETE", "FRESH", from, from.plusSeconds(1), List.of()));

        RiskFactsProvider.Snapshot snapshot = new ModeAwareRiskFactsProvider(liveFacts, authoritative,
                new ObjectMapper().registerModule(new JavaTimeModule()), java.time.Clock.systemUTC(), null)
                .load(mock(Account.class), brokerAccount, brokerId, from, to);

        assertThat(snapshot.complete()).isTrue();
        assertThat(snapshot.account().equity()).isEqualByComparingTo("990");
        verifyNoInteractions(liveFacts);
    }

    @Test
    void staleAuthoritativeV2FactsFailClosed() {
        UUID brokerId = UUID.randomUUID();
        BrokerAccountSnapshotV2Client authoritative = mock(BrokerAccountSnapshotV2Client.class);
        UUID providerReference = UUID.randomUUID();
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.LIVE);
        when(brokerAccount.providerRegistrationReference()).thenReturn(providerReference);
        when(brokerAccount.externalAccountId()).thenReturn("ctrader-1");
        when(authoritative.getAccountSnapshot(brokerId)).thenReturn(new BrokerAccountSnapshotV2Dto(
                brokerId, providerReference, "ctrader-1", Map.of("USD", BigDecimal.ONE), List.of(),
                from.plusSeconds(1), new BrokerAccountFactsV2Dto("USD", BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ONE), List.of(), List.of(), "INCOMPLETE", "STALE", from,
                from.plusSeconds(1), List.of("OBSERVATION_STALE")));

        RiskFactsProvider.Snapshot snapshot = new ModeAwareRiskFactsProvider(liveFacts, authoritative,
                new ObjectMapper().registerModule(new JavaTimeModule()), java.time.Clock.systemUTC(), null)
                .load(mock(Account.class), brokerAccount, brokerId, from, to);

        assertThat(snapshot.complete()).isFalse();
        assertThat(snapshot.unavailabilityReasons()).contains("OBSERVATION_STALE",
                "AUTHORITATIVE_SNAPSHOT_NOT_COMPLETE_AND_FRESH");
        verifyNoInteractions(liveFacts);
    }

    @Test
    void liveMapsAuthoritativePositionMarginAndStopLossToRiskFacts() {
        UUID brokerId = UUID.randomUUID();
        UUID providerReference = UUID.randomUUID();
        BrokerAccountSnapshotV2Client authoritative = mock(BrokerAccountSnapshotV2Client.class);
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.LIVE);
        when(brokerAccount.providerRegistrationReference()).thenReturn(providerReference);
        when(brokerAccount.externalAccountId()).thenReturn("ctrader-1");
        when(authoritative.getAccountSnapshot(brokerId)).thenReturn(new BrokerAccountSnapshotV2Dto(
                brokerId, providerReference, "ctrader-1", Map.of("USD", new BigDecimal("1000")),
                List.of(new com.hope.trading.trading_core.broker.dto.BrokerPositionSnapshotV2Dto(
                        "EUR/USD", new BigDecimal("2"), new BigDecimal("1.1"), from.plusSeconds(1),
                        "position-1", new BigDecimal("5"), new BigDecimal("1.05"))),
                from.plusSeconds(2), new BrokerAccountFactsV2Dto("USD", new BigDecimal("1000"),
                new BigDecimal("990"), new BigDecimal("10"), new BigDecimal("980")), List.of(), List.of(),
                "COMPLETE", "FRESH", from, from.plusSeconds(2), List.of()));

        RiskFactsProvider.Snapshot snapshot = new ModeAwareRiskFactsProvider(liveFacts, authoritative,
                new ObjectMapper().registerModule(new JavaTimeModule()), java.time.Clock.systemUTC(), null)
                .load(mock(Account.class), brokerAccount, brokerId, from, to);

        assertThat(snapshot.complete()).isTrue();
        assertThat(snapshot.positions()).singleElement().satisfies(position -> {
            assertThat(position.margin()).isEqualByComparingTo("5");
            assertThat(position.protectionStatus()).isEqualTo(
                    com.hope.trading.risk.domain.RiskTypes.ProtectionStatus.PROTECTED);
            assertThat(position.protectiveStops()).singleElement().satisfies(stop -> {
                assertThat(stop.stopPrice()).isEqualByComparingTo("1.05");
                assertThat(stop.quantity()).isEqualByComparingTo("2");
            });
        });
    }

    @Test
    void liveKeepsExplicitlyUnprotectedPositionEligibleForExistingRiskRules() {
        UUID brokerId = UUID.randomUUID();
        UUID providerReference = UUID.randomUUID();
        BrokerAccountSnapshotV2Client authoritative = mock(BrokerAccountSnapshotV2Client.class);
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.LIVE);
        when(brokerAccount.providerRegistrationReference()).thenReturn(providerReference);
        when(brokerAccount.externalAccountId()).thenReturn("ctrader-1");
        when(authoritative.getAccountSnapshot(brokerId)).thenReturn(new BrokerAccountSnapshotV2Dto(
                brokerId, providerReference, "ctrader-1", Map.of("USD", new BigDecimal("1000")),
                List.of(new com.hope.trading.trading_core.broker.dto.BrokerPositionSnapshotV2Dto(
                        "EUR/USD", new BigDecimal("2"), new BigDecimal("1.1"), from.plusSeconds(1),
                        "position-1", new BigDecimal("5"), null)),
                from.plusSeconds(2), new BrokerAccountFactsV2Dto("USD", new BigDecimal("1000"),
                new BigDecimal("990"), new BigDecimal("10"), new BigDecimal("980")), List.of(), List.of(),
                "COMPLETE", "FRESH", from, from.plusSeconds(2), List.of()));

        RiskFactsProvider.Snapshot snapshot = new ModeAwareRiskFactsProvider(liveFacts, authoritative,
                new ObjectMapper().registerModule(new JavaTimeModule()), java.time.Clock.systemUTC(), null)
                .load(mock(Account.class), brokerAccount, brokerId, from, to);

        assertThat(snapshot.complete()).isTrue();
        assertThat(snapshot.positions()).singleElement()
                .extracting(RiskFactsProvider.Position::protectionStatus)
                .isEqualTo(com.hope.trading.risk.domain.RiskTypes.ProtectionStatus.UNPROTECTED);
    }

    @Test
    void missingPositionMarginRemainsRiskIncomplete() {
        UUID brokerId = UUID.randomUUID();
        UUID providerReference = UUID.randomUUID();
        BrokerAccountSnapshotV2Client authoritative = mock(BrokerAccountSnapshotV2Client.class);
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.LIVE);
        when(brokerAccount.providerRegistrationReference()).thenReturn(providerReference);
        when(brokerAccount.externalAccountId()).thenReturn("ctrader-1");
        when(authoritative.getAccountSnapshot(brokerId)).thenReturn(new BrokerAccountSnapshotV2Dto(
                brokerId, providerReference, "ctrader-1", Map.of("USD", new BigDecimal("1000")),
                List.of(new com.hope.trading.trading_core.broker.dto.BrokerPositionSnapshotV2Dto(
                        "EUR/USD", new BigDecimal("2"), new BigDecimal("1.1"), from.plusSeconds(1),
                        "position-1", null, new BigDecimal("1.05"))),
                from.plusSeconds(2), new BrokerAccountFactsV2Dto("USD", new BigDecimal("1000"),
                new BigDecimal("990"), new BigDecimal("10"), new BigDecimal("980")), List.of(), List.of(),
                "COMPLETE", "FRESH", from, from.plusSeconds(2), List.of()));

        RiskFactsProvider.Snapshot snapshot = new ModeAwareRiskFactsProvider(liveFacts, authoritative,
                new ObjectMapper().registerModule(new JavaTimeModule()), java.time.Clock.systemUTC(), null)
                .load(mock(Account.class), brokerAccount, brokerId, from, to);

        assertThat(snapshot.complete()).isFalse();
        assertThat(snapshot.unavailabilityReasons()).contains("POSITION_MARGIN_UNAVAILABLE:position-1");
    }

    @Test
    void liveFallsBackToLegacyFactsWhenV2IsUnavailable() {
        UUID brokerId = UUID.randomUUID();
        BrokerAccountSnapshotV2Client authoritative = mock(BrokerAccountSnapshotV2Client.class);
        BrokerRiskFactsPort.Snapshot legacy = new BrokerRiskFactsPort.Snapshot(
                brokerId, 2, from.plusSeconds(1), true, List.of(), Map.of(),
                new BrokerRiskFactsPort.Account("USD", BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ZERO, BigDecimal.ONE), List.of(), List.of(), List.of(), "{}");
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.LIVE);
        Request request = Request.create(Request.HttpMethod.GET, "http://broker", Map.of(),
                (byte[]) null, null);
        when(authoritative.getAccountSnapshot(brokerId)).thenThrow(new RetryableException(
                503, "timeout", Request.HttpMethod.GET, (Long) null, request));
        when(liveFacts.load(brokerId, from, to)).thenReturn(legacy);

        RiskFactsProvider.Snapshot snapshot = new ModeAwareRiskFactsProvider(liveFacts, authoritative,
                new ObjectMapper(), java.time.Clock.systemUTC(), null)
                .load(mock(Account.class), brokerAccount, brokerId, from, to);

        assertThat(snapshot.sourceVersion()).isEqualTo(2);
        verify(liveFacts).load(brokerId, from, to);
    }

    @Test
    void authoritativeDealsAreLimitedToRiskWindowAndKeepSettlementAsset() {
        UUID brokerId = UUID.randomUUID();
        UUID providerReference = UUID.randomUUID();
        BrokerAccountSnapshotV2Client authoritative = mock(BrokerAccountSnapshotV2Client.class);
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.LIVE);
        when(brokerAccount.providerRegistrationReference()).thenReturn(providerReference);
        when(brokerAccount.externalAccountId()).thenReturn("ctrader-1");
        var deal = new com.hope.trading.trading_core.broker.dto.BrokerDealSnapshotV2Dto(
                "deal-1", "EUR/USD", "EUR", "BUY", BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN, from.plusSeconds(1));
        var outside = new com.hope.trading.trading_core.broker.dto.BrokerDealSnapshotV2Dto(
                "deal-2", "EUR/USD", "JPY", "BUY", BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN, to.plusSeconds(1));
        when(authoritative.getAccountSnapshot(brokerId)).thenReturn(new BrokerAccountSnapshotV2Dto(
                brokerId, providerReference, "ctrader-1", Map.of("USD", new BigDecimal("1000")), List.of(),
                from.plusSeconds(2), new BrokerAccountFactsV2Dto("USD", new BigDecimal("1000"),
                new BigDecimal("990"), new BigDecimal("10"), new BigDecimal("980")), List.of(),
                List.of(deal, outside), "COMPLETE", "FRESH", from, from.plusSeconds(2), List.of()));

        RiskFactsProvider.Snapshot snapshot = new ModeAwareRiskFactsProvider(liveFacts, authoritative,
                new ObjectMapper().registerModule(new JavaTimeModule()), java.time.Clock.systemUTC(), null)
                .load(mock(Account.class), brokerAccount, brokerId, from, to);

        assertThat(snapshot.closedTrades()).singleElement()
                .satisfies(value -> {
                    assertThat(value.settlementAsset()).isEqualTo("EUR");
                    assertThat(value.sourceTradeReference()).isEqualTo("deal-1");
                });
    }

    @Test
    void authoritativeIdentityMismatchFailsClosedWithoutLegacyFallback() {
        UUID brokerId = UUID.randomUUID();
        BrokerAccountSnapshotV2Client authoritative = mock(BrokerAccountSnapshotV2Client.class);
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.LIVE);
        when(brokerAccount.providerRegistrationReference()).thenReturn(UUID.randomUUID());
        when(brokerAccount.externalAccountId()).thenReturn("ctrader-1");
        when(authoritative.getAccountSnapshot(brokerId)).thenReturn(new BrokerAccountSnapshotV2Dto(
                UUID.randomUUID(), UUID.randomUUID(), "ctrader-1", Map.of("USD", BigDecimal.ONE), List.of(),
                from.plusSeconds(1), null, List.of(), List.of(), "COMPLETE", "FRESH", from,
                from.plusSeconds(1), List.of()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ModeAwareRiskFactsProvider(
                liveFacts, authoritative, new ObjectMapper(), java.time.Clock.systemUTC(), null)
                .load(mock(Account.class), brokerAccount, brokerId, from, to))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authoritative broker snapshot identity mismatch");
        verifyNoInteractions(liveFacts);
    }

    @Test
    void liveUsesBrokerFactsAndDoesNotReadLocalState() {
        UUID brokerId = UUID.randomUUID();
        Account account = mock(Account.class);
        BrokerRiskFactsPort.Snapshot brokerSnapshot = new BrokerRiskFactsPort.Snapshot(
                brokerId, 1, from.plusSeconds(1), true, List.of(), Map.of(),
                new BrokerRiskFactsPort.Account("USD", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE),
                List.of(), List.of(), List.of(), "{}");
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.LIVE);
        when(liveFacts.load(brokerId, from, to)).thenReturn(brokerSnapshot);
        ModeAwareRiskFactsProvider provider = new ModeAwareRiskFactsProvider(liveFacts, new ObjectMapper());

        assertThat(provider.load(account, brokerAccount, brokerId, from, to).sourceId()).isEqualTo(brokerId);
        verify(liveFacts).load(brokerId, from, to);
        verifyNoInteractions(account);
    }

    @Test
    void paperUsesLocalBalancesAndOpenTradesWithoutCallingBrokerFacts() {
        UUID brokerId = UUID.randomUUID();
        UUID tradeId = UUID.randomUUID();
        Account account = Account.builder().accountId(UUID.randomUUID()).baseCurrency("USD")
                .equity(new BigDecimal("1000")).balances(List.of(
                        AccountBalance.builder().asset("USD").amount(new BigDecimal("1000")).build()))
                .trades(List.of(Trade.builder().tradeId(tradeId).symbol("BTC/USD").type(TradeType.BUY)
                        .entryPrice(new BigDecimal("100")).quantity(BigDecimal.ONE)
                        .tradeStatus(TradeStatus.OPEN).build()))
                .build();
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.PAPER);
        when(brokerAccount.id()).thenReturn(brokerId);
        ModeAwareRiskFactsProvider provider = new ModeAwareRiskFactsProvider(liveFacts, new ObjectMapper());

        RiskFactsProvider.Snapshot snapshot = provider.load(account, brokerAccount, brokerId, from, to);

        assertThat(snapshot.assetBalances()).containsEntry("USD", new BigDecimal("1000"));
        assertThat(snapshot.positions()).singleElement().extracting(RiskFactsProvider.Position::positionId)
                .isEqualTo(tradeId);
        assertThat(snapshot.complete()).isTrue();
        assertThat(snapshot.unavailabilityReasons()).isEmpty();
        assertThat(snapshot.positions().get(0).protectedQuantity()).isZero();
        assertThat(snapshot.positions().get(0).protectiveStops()).isEmpty();
        assertThat(snapshot.positions().get(0).protectionStatus().name()).isEqualTo("UNPROTECTED");
        assertThat(snapshot.positions().get(0).margin()).isEqualByComparingTo(new BigDecimal("100"));
        verifyNoInteractions(liveFacts);
    }

    @Test
    void paperCashRefreshesValuationBeforeRiskFacts() {
        UUID brokerId = UUID.randomUUID();
        Account account = Account.builder().accountId(UUID.randomUUID()).baseCurrency("USD")
                .settlementModel(SettlementModel.CASH).realizedBalance(new BigDecimal("1200"))
                .equity(new BigDecimal("1100")).balances(List.of(
                        AccountBalance.builder().asset("USD").amount(new BigDecimal("1000")).build()))
                .build();
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.PAPER);
        when(brokerAccount.id()).thenReturn(brokerId);
        PaperDerivativeAccountValuationService valuation = mock(PaperDerivativeAccountValuationService.class);
        when(valuation.refresh(account)).thenReturn(new PaperDerivativeAccountValuationService.ValuationResult(
                new BigDecimal("1200"), new BigDecimal("-100"), new BigDecimal("1100"),
                AccountValuationStatus.FRESH, from, from, null));
        ModeAwareRiskFactsProvider provider = new ModeAwareRiskFactsProvider(liveFacts, new ObjectMapper(),
                java.time.Clock.systemUTC(), valuation);

        RiskFactsProvider.Snapshot snapshot = provider.load(account, brokerAccount, brokerId, from, to);

        assertThat(snapshot.complete()).isTrue();
        assertThat(snapshot.account().balance()).isEqualByComparingTo("1200");
        assertThat(snapshot.account().equity()).isEqualByComparingTo("1100");
        verify(valuation).refresh(account);
    }

    @Test
    void completeProtectiveStopOrdersOverrideMissingAggregateQuantity() {
        BigDecimal quantity = BigDecimal.TEN;

        assertThat(RiskFactsProvider.protectionStatus(BigDecimal.ZERO, quantity,
                List.of(new RiskFactsProvider.Stop("stop-1", "provider", quantity, new BigDecimal("90")))))
                .isEqualTo(com.hope.trading.risk.domain.RiskTypes.ProtectionStatus.PROTECTED);
    }

    @Test
    void unavailableExecutionModeDoesNotFallBackToPaper() {
        when(brokerAccount.executionMode()).thenReturn(null);
        ModeAwareRiskFactsProvider provider = new ModeAwareRiskFactsProvider(liveFacts, new ObjectMapper());
        Account account = mock(Account.class);
        UUID sourceId = UUID.randomUUID();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> provider.load(
                account, brokerAccount, sourceId, from, to))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(liveFacts);
    }

    @Test
    void paperLocalFactsRemainCompleteWithoutOrderMargin() {
        UUID brokerId = UUID.randomUUID();
        Account account = Account.builder().accountId(UUID.randomUUID()).baseCurrency("USD")
                .equity(new BigDecimal("1000")).balances(List.of(
                        AccountBalance.builder().asset("USD").amount(new BigDecimal("1000")).build()))
                .trades(List.of()).build();
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.PAPER);
        when(brokerAccount.id()).thenReturn(brokerId);

        RiskFactsProvider.Snapshot snapshot = new ModeAwareRiskFactsProvider(liveFacts,
                new ObjectMapper().registerModule(new JavaTimeModule()))
                .load(account, brokerAccount, brokerId, from, to);

        assertThat(snapshot.complete()).isTrue();
        assertThat(snapshot.account().margin()).isZero();
        assertThat(snapshot.sourceVersion()).isEqualTo(1);
        assertThat(snapshot.unavailabilityReasons()).isEmpty();
        verifyNoInteractions(liveFacts);
    }

    @Test
    void paperSourceVersionAndPayloadCoverAccountAndTradeVersions() {
        UUID brokerId = UUID.randomUUID();
        UUID tradeId = UUID.randomUUID();
        Account account = Account.builder().accountId(UUID.randomUUID()).baseCurrency("usd")
                .version(4).equity(new BigDecimal("1000")).balances(List.of(
                        AccountBalance.builder().asset("USD").amount(new BigDecimal("1000")).build()))
                .trades(List.of(Trade.builder().tradeId(tradeId).version(7).symbol("BTC/USD").type(TradeType.BUY)
                        .entryPrice(new BigDecimal("100")).quantity(BigDecimal.ONE)
                        .tradeStatus(TradeStatus.OPEN).build()))
                .build();
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.PAPER);
        when(brokerAccount.id()).thenReturn(brokerId);

        RiskFactsProvider.Snapshot snapshot = new ModeAwareRiskFactsProvider(liveFacts,
                new ObjectMapper().registerModule(new JavaTimeModule()))
                .load(account, brokerAccount, brokerId, from, to);

        assertThat(snapshot.sourceVersion()).isEqualTo(7);
        assertThat(snapshot.account().valuationAsset()).isEqualTo("USD");
        assertThat(snapshot.sourcePayload()).contains("accountVersion", "tradeVersions", tradeId.toString());
    }

    @Test
    void paperClosedTradeMayHaveZeroStopLossSentinel() {
        UUID brokerId = UUID.randomUUID();
        Account account = Account.builder().accountId(UUID.randomUUID()).baseCurrency("USD")
                .equity(new BigDecimal("1000")).balances(List.of(
                        AccountBalance.builder().asset("USD").amount(new BigDecimal("1000")).build()))
                .trades(List.of(Trade.builder().tradeId(UUID.randomUUID()).symbol("PEPE/EUR")
                        .type(TradeType.SELL).entryPrice(new BigDecimal("0.000003829"))
                        .quantity(new BigDecimal("75000000")).stopLoss(BigDecimal.ZERO)
                        .takeProfit(BigDecimal.ZERO).pnl(new BigDecimal("-12.75"))
                        .closedAt(to.minusSeconds(1)).tradeStatus(TradeStatus.CLOSED).build()))
                .build();
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.PAPER);
        when(brokerAccount.id()).thenReturn(brokerId);

        RiskFactsProvider.Snapshot snapshot = new ModeAwareRiskFactsProvider(liveFacts,
                new ObjectMapper().registerModule(new JavaTimeModule()))
                .load(account, brokerAccount, brokerId, from, to);

        assertThat(snapshot.complete()).isTrue();
        assertThat(snapshot.unavailabilityReasons()).isEmpty();
        assertThat(snapshot.closedTrades()).singleElement()
                .satisfies(closedTrade -> {
                    assertThat(closedTrade.realizedPnl()).isEqualByComparingTo(new BigDecimal("-12.75"));
                    assertThat(closedTrade.settlementAsset()).isEqualTo("USD");
                });
    }

    @Test
    void paperInvalidCurrencyFailsClosedWithoutInventingUsd() {
        UUID brokerId = UUID.randomUUID();
        Account account = Account.builder().accountId(UUID.randomUUID()).baseCurrency(" ")
                .equity(new BigDecimal("1000")).balances(List.of(
                        AccountBalance.builder().asset("EUR").amount(new BigDecimal("1000")).build()))
                .trades(List.of()).build();
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.PAPER);
        when(brokerAccount.id()).thenReturn(brokerId);

        RiskFactsProvider.Snapshot snapshot = new ModeAwareRiskFactsProvider(liveFacts, new ObjectMapper())
                .load(account, brokerAccount, brokerId, from, to);

        assertThat(snapshot.complete()).isFalse();
        assertThat(snapshot.account().valuationAsset()).isNull();
        assertThat(snapshot.account().balance()).isNull();
        assertThat(snapshot.unavailabilityReasons()).contains("PAPER_ACCOUNT_FACTS_INVALID");
        assertThat(snapshot.unavailabilityReasons()).contains("PAPER_ACCOUNT_VALUATION_INCOMPLETE");
    }

    @Test
    void paperNullAccountReturnsIncompleteFactsInsteadOfThrowing() {
        UUID brokerId = UUID.randomUUID();
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.PAPER);
        when(brokerAccount.id()).thenReturn(brokerId);

        RiskFactsProvider.Snapshot snapshot = new ModeAwareRiskFactsProvider(liveFacts, new ObjectMapper())
                .load(null, brokerAccount, brokerId, from, to);

        assertThat(snapshot.complete()).isFalse();
        assertThat(snapshot.sourceVersion()).isZero();
        assertThat(snapshot.unavailabilityReasons()).contains("PAPER_ACCOUNT_FACTS_INVALID");
    }
}
