package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.broker.apiClient.BrokerApiClient;
import com.hope.trading.trading_core.broker.dto.AccountBalanceDto;
import com.hope.trading.trading_core.broker.dto.BrokerAccountDto;
import com.hope.trading.trading_core.dashboard.integration.BrokerDashboardMapper;
import com.hope.trading.trading_core.dashboard.integration.MarketDataDashboardMapper;
import com.hope.trading.trading_core.dashboard.model.*;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.dto.Position;
import com.hope.trading.trading_core.exception.EntityNotFoundException;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.dto.*;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.Rules;
import com.hope.trading.trading_core.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DashboardQueryServiceTest {
    private final AccountService accountService = mock(AccountService.class);
    private final BrokerAccountRepository brokerAccounts = mock(BrokerAccountRepository.class);
    private final BrokerApiClient brokerClient = mock(BrokerApiClient.class);
    private final MarketDataClient marketClient = mock(MarketDataClient.class);
    private final AccountBalanceValuationService balanceValuation = mock(AccountBalanceValuationService.class);
    private final TradeAnalyticsService analytics = mock(TradeAnalyticsService.class);
    private final RiskEngine riskEngine = mock(RiskEngine.class);
    private DashboardQueryService service;
    private Account account;
    private UUID btcId;
    private UUID ethId;

    @BeforeEach
    void setUp() {
        account = account();
        btcId = UUID.randomUUID();
        ethId = UUID.randomUUID();
        PositionQueryService positionQueryService = new PositionQueryService(
                marketClient,
                new MarketDataDashboardMapper(),
                new PositionValuationService(new TradingCalculatorServiceImpl())
        );
        service = new DashboardQueryService(
                accountService,
                brokerAccounts,
                brokerClient,
                new BrokerDashboardMapper(),
                positionQueryService,
                balanceValuation,
                new AccountEquityService(),
                new DashboardFreshnessService(),
                new DashboardAlertService(),
                analytics,
                riskEngine
        );
        when(accountService.getAccountById(account.getAccountId(), "alice")).thenReturn(account);
        when(balanceValuation.value(anyString(), anyMap()))
                .thenReturn(new AccountValuationResult(new BigDecimal("1000"), "COMPLETE",
                        Instant.now(), "test-policy", null));
        when(analytics.getTodayPnL(account.getAccountId())).thenReturn(new BigDecimal("-10"));
        when(riskEngine.evaluateDashboard(any(), any(), any(), any()))
                .thenReturn(new DashboardRiskEvaluation(RiskStatus.SAFE, List.of()));
    }

    @Test
    void buildsDashboardWithoutPosition() {
        when(brokerClient.getAccount()).thenReturn(broker(List.of()));

        DashboardSummary result = service.findDashboard(account.getAccountId(), "alice");

        assertThat(result.openPositions()).isEmpty();
        assertThat(result.account().equity()).isEqualByComparingTo("1000");
        assertThat(result.freshness().status()).isEqualTo(DashboardDataStatus.LIVE);
        verify(marketClient, never()).findPriceSnapshots(any());
    }

    @Test
    void buildsCompleteDashboardWithMultiplePositionsAndOneGroupedPriceCall() {
        when(brokerClient.getAccount()).thenReturn(broker(List.of(
                position("p1", "BTCUSD", "buy", "100", "2"),
                position("p2", "ETH/USD", "sell", "200", "1")
        )));
        when(marketClient.findAll()).thenReturn(List.of(
                market(btcId, "BTC/USD"), market(ethId, "ETH/USD")
        ));
        when(marketClient.findPriceSnapshots(any())).thenReturn(List.of(
                price(btcId, "BTC/USD", "110"),
                price(ethId, "ETH/USD", "180")
        ));

        DashboardSummary result = service.findDashboard(account.getAccountId(), "alice");

        assertThat(result.openPositions()).hasSize(2);
        assertThat(result.openPositions().get(0).unrealizedPnl()).isEqualByComparingTo("20");
        assertThat(result.openPositions().get(1).unrealizedPnl()).isEqualByComparingTo("20");
        assertThat(result.account().equity()).isEqualByComparingTo("1040");
        assertThat(result.openPositions()).allMatch(position -> position.exposure().signum() > 0);
        assertThat(result.alerts()).extracting(DashboardAlert::code)
                .contains("MISSING_STOP_LOSS");

        ArgumentCaptor<MarketPriceSnapshotRequest> captor =
                ArgumentCaptor.forClass(MarketPriceSnapshotRequest.class);
        verify(marketClient, times(2)).findPriceSnapshots(captor.capture());
        assertThat(captor.getAllValues()).hasSize(2);
    }

    @Test
    void keepsPositionsAndMarksDashboardDegradedWhenMarketDataFails() {
        when(brokerClient.getAccount()).thenReturn(broker(List.of(
                position("p1", "BTC/USD", "buy", "100", "2")
        )));
        when(marketClient.findAll()).thenThrow(new IllegalStateException("unavailable"));

        DashboardSummary result = service.findDashboard(account.getAccountId(), "alice");

        assertThat(result.openPositions()).hasSize(1);
        assertThat(result.openPositions().getFirst().currentPrice()).isNull();
        assertThat(result.openPositions().getFirst().unrealizedPnl()).isNull();
        assertThat(result.freshness().status()).isEqualTo(DashboardDataStatus.DEGRADED);
        assertThat(result.alerts()).extracting(DashboardAlert::code)
                .contains("MARKET_PRICE_UNAVAILABLE");
    }

    @Test
    void returnsUnavailableDashboardWhenBrokerFails() {
        when(brokerClient.getAccount()).thenThrow(new IllegalStateException("unavailable"));

        DashboardSummary result = service.findDashboard(account.getAccountId(), "alice");

        assertThat(result.freshness().status()).isEqualTo(DashboardDataStatus.UNAVAILABLE);
        assertThat(result.account().equity()).isNull();
        assertThat(result.openPositions()).isEmpty();
    }

    @Test
    void doesNotFabricateEquityWhenAssetValuationIsUnavailable() {
        when(balanceValuation.value(anyString(), anyMap()))
                .thenReturn(AccountValuationResult.unavailable("valuation unavailable"));
        when(brokerClient.getAccount()).thenReturn(broker(List.of()));

        DashboardSummary result = service.findDashboard(account.getAccountId(), "alice");

        assertThat(result.account().equity()).isNull();
        assertThat(result.account().equitySource()).isEqualTo("UNAVAILABLE");
        assertThat(result.risk().status()).isEqualTo(RiskStatus.UNAVAILABLE);
        assertThat(result.freshness().status()).isEqualTo(DashboardDataStatus.DEGRADED);
    }

    @Test
    void checksAccountOwnershipBeforeCallingExternalServices() {
        UUID foreignAccountId = UUID.randomUUID();
        when(accountService.getAccountById(foreignAccountId, "alice"))
                .thenThrow(new EntityNotFoundException("account not found"));

        assertThatThrownBy(() -> service.findDashboard(foreignAccountId, "alice"))
                .isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(brokerClient);
        verifyNoInteractions(marketClient);
    }

    @Test
    void usesPersistedBalancesAndPositionsForPaperAccounts() {
        BrokerAccount paper = BrokerAccount.create(
                UUID.randomUUID(), BrokerProvider.KRAKEN, ExecutionMode.PAPER,
                "Paper", Instant.now());
        account.setBrokerAccountId(paper.id());
        when(brokerAccounts.findById(paper.id())).thenReturn(java.util.Optional.of(paper));

        DashboardSummary result = service.findDashboard(account.getAccountId(), "alice");

        assertThat(result.account().equity()).isEqualByComparingTo("1000");
        verifyNoInteractions(brokerClient);
        verify(balanceValuation).value(eq("USD"), argThat(values ->
                values.size() == 1 && values.get("USD").compareTo(new BigDecimal("1000")) == 0));
    }

    @Test
    void doesNotDoubleCountPaperPositionPnlIncludedInBalances() {
        BrokerAccount paper = BrokerAccount.create(
                UUID.randomUUID(), BrokerProvider.KRAKEN, ExecutionMode.PAPER,
                "Paper", Instant.now());
        account.setBrokerAccountId(paper.id());
        account.addBalance(AccountBalance.builder()
                .asset("BTC")
                .amount(BigDecimal.ONE)
                .build());
        account.addTrade(com.hope.trading.trading_core.model.Trade.builder()
                .tradeId(UUID.randomUUID())
                .symbol("BTC/USD")
                .type(com.hope.trading.trading_core.helper.TradeType.BUY)
                .entryPrice(new BigDecimal("100"))
                .quantity(BigDecimal.ONE)
                .openedAt(Instant.now().minusSeconds(60))
                .tradeStatus(com.hope.trading.trading_core.helper.TradeStatus.OPEN)
                .build());
        when(brokerAccounts.findById(paper.id())).thenReturn(java.util.Optional.of(paper));
        when(balanceValuation.value(anyString(), anyMap()))
                .thenReturn(new AccountValuationResult(new BigDecimal("1010"), "COMPLETE",
                        Instant.now(), "test-policy", null));
        when(marketClient.findAll()).thenReturn(List.of(market(btcId, "BTC/USD")));
        when(marketClient.findPriceSnapshots(any())).thenReturn(List.of(
                price(btcId, "BTC/USD", "110")));

        DashboardSummary result = service.findDashboard(account.getAccountId(), "alice");

        assertThat(result.account().equity()).isEqualByComparingTo("1010");
    }

    private Account account() {
        Account result = Account.builder()
                .accountId(UUID.randomUUID())
                .broker("KRAKEN")
                .name("Primary")
                .baseCurrency("USD")
                .equity(new BigDecimal("1000"))
                .peakEquity(new BigDecimal("1100"))
                .rules(Rules.builder()
                        .active(true)
                        .maxRiskPerTrade(new BigDecimal("0.02"))
                        .maxDailyLoss(new BigDecimal("0.05"))
                        .maxTotalDrawdown(new BigDecimal("0.10"))
                        .build())
                .build();
        result.addBalance(AccountBalance.builder()
                .asset("USD")
                .amount(new BigDecimal("1000"))
                .build());
        return result;
    }

    private BrokerAccountDto broker(List<Position> positions) {
        return BrokerAccountDto.builder()
                .brokerAccountId("kraken-default")
                .broker("KRAKEN")
                .baseCurrency("USD")
                .balances(AccountBalanceDto.builder()
                        .balances(Map.of("USD", new BigDecimal("1000")))
                        .build())
                .openTrades(positions)
                .dataAt(Instant.now())
                .build();
    }

    private Position position(
            String id, String symbol, String side, String entryPrice, String quantity
    ) {
        return Position.builder()
                .brokerPositionId(id)
                .symbol(symbol)
                .side(side)
                .entryPrice(new BigDecimal(entryPrice))
                .quantity(new BigDecimal(quantity))
                .openedAt(Instant.now().minusSeconds(3600))
                .dataAt(Instant.now())
                .build();
    }

    private MarketResponse market(UUID id, String symbol) {
        return MarketResponse.builder().marketId(id).symbol(symbol).quoteAsset("USD").build();
    }

    private MarketPriceSnapshotDto price(UUID id, String symbol, String price) {
        return new MarketPriceSnapshotDto(
                id, symbol, new BigDecimal(price), new BigDecimal(price),
                new BigDecimal(price), true, Instant.now(),
                MarketPriceSnapshotStatus.FRESH
        );
    }
}
