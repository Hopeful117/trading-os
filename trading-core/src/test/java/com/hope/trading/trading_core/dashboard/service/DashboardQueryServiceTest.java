package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.broker.apiClient.BrokerApiClient;
import com.hope.trading.trading_core.broker.dto.AccountBalanceDto;
import com.hope.trading.trading_core.broker.dto.BrokerAccountDto;
import com.hope.trading.trading_core.dashboard.integration.BrokerDashboardMapper;
import com.hope.trading.trading_core.dashboard.integration.MarketDataDashboardMapper;
import com.hope.trading.trading_core.dashboard.model.*;
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
    private final BrokerApiClient brokerClient = mock(BrokerApiClient.class);
    private final MarketDataClient marketClient = mock(MarketDataClient.class);
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
        service = new DashboardQueryService(
                accountService,
                brokerClient,
                marketClient,
                new BrokerDashboardMapper(),
                new MarketDataDashboardMapper(),
                new PositionValuationService(new TradingCalculatorServiceImpl()),
                new AccountEquityService(),
                new DashboardFreshnessService(),
                new DashboardAlertService(),
                analytics,
                riskEngine
        );
        when(accountService.getAccountById(account.getAccountId(), "alice")).thenReturn(account);
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
        verify(marketClient, times(1)).findPriceSnapshots(captor.capture());
        assertThat(captor.getValue().marketIds()).containsExactlyInAnyOrder(btcId, ethId);
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
    void checksAccountOwnershipBeforeCallingExternalServices() {
        UUID foreignAccountId = UUID.randomUUID();
        when(accountService.getAccountById(foreignAccountId, "alice"))
                .thenThrow(new EntityNotFoundException("account not found"));

        assertThatThrownBy(() -> service.findDashboard(foreignAccountId, "alice"))
                .isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(brokerClient);
        verifyNoInteractions(marketClient);
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
        return MarketResponse.builder().marketId(id).symbol(symbol).build();
    }

    private MarketPriceSnapshotDto price(UUID id, String symbol, String price) {
        return new MarketPriceSnapshotDto(
                id, symbol, new BigDecimal(price), new BigDecimal(price),
                new BigDecimal(price), true, Instant.now(),
                MarketPriceSnapshotStatus.FRESH
        );
    }
}
