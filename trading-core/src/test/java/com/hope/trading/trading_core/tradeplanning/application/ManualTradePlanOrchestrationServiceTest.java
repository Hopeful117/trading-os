package com.hope.trading.trading_core.tradeplanning.application;

import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.market_data.dto.MarketState;
import com.hope.trading.trading_core.market_data.service.MarketService;
import com.hope.trading.trading_core.tradeplanning.api.ManualTradePlanRequest;
import com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualTradePlanOrchestrationServiceTest {
    private static final UUID MARKET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID actorId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private AccountRepository accounts;
    private TradePlanningProfileService profiles;
    private MarketService markets;
    private MarketIntelligenceTradePlanningClient marketIntelligence;
    private ManualTradePlanOrchestrationService service;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        profiles = mock(TradePlanningProfileService.class);
        markets = mock(MarketService.class);
        marketIntelligence = mock(MarketIntelligenceTradePlanningClient.class);
        service = new ManualTradePlanOrchestrationService(
                accounts, profiles, markets, marketIntelligence,
                Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), java.time.ZoneOffset.UTC));
        when(accounts.findById(accountId)).thenReturn(Optional.of(account(actorId)));
        when(profiles.effective(actorId, accountId)).thenReturn(profile());
        MarketResponse market = mock(MarketResponse.class);
        MarketState marketState = mock(MarketState.class);
        when(market.getMarketId()).thenReturn(MARKET_ID);
        when(market.getSymbol()).thenReturn("BTC/USD");
        when(market.getMarketState()).thenReturn(marketState);
        when(marketState.isTradable()).thenReturn(true);
        when(markets.findAll()).thenReturn(List.of(market));
        when(marketIntelligence.createManual(eq("key-1"), any()))
                .thenReturn(mock(MarketIntelligenceTradePlanningClient.PlanTransport.class));
    }

    @Test
    void createsManualPlanWithServerOwnedContextAndDerivedValues() {
        service.create(actorId, request(null, "MARKET"), "key-1");

        verify(marketIntelligence).createManual(eq("key-1"),
                org.mockito.ArgumentMatchers.argThat(value ->
                        value.actorId().equals(actorId)
                                && value.accountId().equals(accountId)
                                && value.context().ownerId().equals(actorId)
                                && value.context().tradingAccountId().equals(accountId)
                                && value.notional().compareTo(BigDecimal.valueOf(200)) == 0
                                && value.expiresAt().equals(Instant.parse("2026-09-20T13:00:00Z"))));
    }

    @Test
    void rejectsUnsupportedOrderTypeBeforeCallingMarketIntelligence() {
        assertThatThrownBy(() -> service.create(actorId, request(BigDecimal.valueOf(100), "STOP"), "key-1"))
                .isInstanceOf(ManualTradePlanException.class)
                .extracting("code")
                .isEqualTo("UNSUPPORTED_ENTRY_TYPE");
        org.mockito.Mockito.verifyNoInteractions(marketIntelligence);
    }

    @Test
    void rejectsForeignAccount() {
        when(accounts.findById(accountId)).thenReturn(Optional.of(account(UUID.randomUUID())));

        assertThatThrownBy(() -> service.create(actorId, request(null, "MARKET"), "key-1"))
                .isInstanceOf(ManualTradePlanException.class)
                .extracting("code")
                .isEqualTo("ACCOUNT_FORBIDDEN");
    }

    private Account account(UUID ownerId) {
        Account account = new Account();
        User user = new User();
        user.setUserId(ownerId);
        account.setUser(user);
        account.setBaseCurrency("USD");
        return account;
    }

    private com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile profile() {
        UUID profileId = UUID.randomUUID();
        return new com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile(profileId, 1, actorId,
                new com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile.RiskBudget(
                        BigDecimal.valueOf(100), "USD", profileId, 1),
                new com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile.PlanningPreferences(
                        profileId, 1,
                        com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile.EntryType.MARKET,
                        com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile.StopStrategy.PERCENTAGE_DISTANCE,
                        BigDecimal.ONE,
                        com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile.TargetStrategy.RISK_MULTIPLE,
                        BigDecimal.valueOf(2),
                        com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile.PlanningHorizon.INTRADAY,
                        Duration.ofHours(1)),
                Instant.parse("2026-09-20T11:00:00Z"));
    }

    private ManualTradePlanRequest request(BigDecimal entryPrice, String entryType) {
        return new ManualTradePlanRequest(accountId, MARKET_ID, "LONG", entryType, entryPrice,
                BigDecimal.valueOf(100), BigDecimal.valueOf(90), "Invalidation below support",
                List.of(new ManualTradePlanRequest.TakeProfit(BigDecimal.valueOf(120), BigDecimal.valueOf(100))),
                BigDecimal.valueOf(2), BigDecimal.valueOf(5), "Manual setup",
                Set.of("Price confirms"), Set.of("Support breaks"), Set.of());
    }
}
