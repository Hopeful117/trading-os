package com.hope.trading.trading_core.tradeplanning.application;

import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile;
import com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpportunityTradePlanOrchestrationServiceTest {
    private AccountRepository accounts;
    private TradePlanningProfileService profiles;
    private MarketIntelligenceTradePlanningClient marketIntelligence;
    private OpportunityTradePlanOrchestrationService service;

    private final UUID actorId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID opportunityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        profiles = mock(TradePlanningProfileService.class);
        marketIntelligence = mock(MarketIntelligenceTradePlanningClient.class);
        service = new OpportunityTradePlanOrchestrationService(
                accounts, profiles, marketIntelligence, Clock.systemUTC());
        when(accounts.findById(accountId)).thenReturn(Optional.of(account()));
        when(profiles.effective(actorId, accountId)).thenReturn(profile("EUR"));
    }

    private Account account() {
        Account account = new Account();
        User user = new User();
        user.setUserId(actorId);
        account.setUser(user);
        account.setBaseCurrency("EUR");
        return account;
    }

    private TradePlanningProfile profile(String currency) {
        UUID profileId = UUID.randomUUID();
        return new TradePlanningProfile(profileId, 3, actorId,
                new TradePlanningProfile.RiskBudget(BigDecimal.TEN, currency, profileId, 3),
                new TradePlanningProfile.PlanningPreferences(profileId, 3,
                        TradePlanningProfile.EntryType.LIMIT,
                        TradePlanningProfile.StopStrategy.PERCENTAGE_DISTANCE,
                        BigDecimal.ONE,
                        TradePlanningProfile.TargetStrategy.RISK_MULTIPLE,
                        BigDecimal.valueOf(2),
                        TradePlanningProfile.PlanningHorizon.INTRADAY,
                        Duration.ofHours(1)),
                Instant.now());
    }

    @Test
    void createsPlanThroughMarketIntelligenceWithCapturedContext() {
        when(marketIntelligence.generateFromOpportunity(
                eq(opportunityId), eq("key-1"), any()))
                .thenReturn(new MarketIntelligenceTradePlanningClient.Response(
                        UUID.randomUUID(), 1));

        var response = service.createFromOpportunity(actorId, opportunityId, accountId, "key-1");

        assertThat(response.tradePlanVersion()).isEqualTo(1);
        org.mockito.Mockito.verify(marketIntelligence).generateFromOpportunity(
                eq(opportunityId), eq("key-1"),
                argThat((MarketIntelligenceTradePlanningClient.Request sent) ->
                        sent.actorId().equals(actorId)
                                && sent.accountId().equals(accountId)
                                && sent.context().ownerId().equals(actorId)
                                && sent.context().tradingAccountId().equals(accountId)
                                && sent.context().accountCurrency().equals("EUR")));
    }

    @Test
    void unknownAccountFailsExplicitly() {
        when(accounts.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createFromOpportunity(
                actorId, opportunityId, accountId, "key-1"))
                .isInstanceOf(AnalysisTradePlanGenerationException.class)
                .satisfies(e -> assertThat(((AnalysisTradePlanGenerationException) e).code())
                        .isEqualTo("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void foreignAccountIsForbidden() {
        Account foreign = account();
        User other = new User();
        other.setUserId(UUID.randomUUID());
        foreign.setUser(other);
        when(accounts.findById(accountId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.createFromOpportunity(
                actorId, opportunityId, accountId, "key-1"))
                .isInstanceOf(AnalysisTradePlanGenerationException.class)
                .satisfies(e -> {
                    assertThat(((AnalysisTradePlanGenerationException) e).code())
                            .isEqualTo("ACCOUNT_FORBIDDEN");
                    assertThat(((AnalysisTradePlanGenerationException) e).status()).isEqualTo(403);
                });
    }

    @Test
    void profileCurrencyMismatchFails() {
        when(profiles.effective(actorId, accountId)).thenReturn(profile("USD"));

        assertThatThrownBy(() -> service.createFromOpportunity(
                actorId, opportunityId, accountId, "key-1"))
                .isInstanceOf(AnalysisTradePlanGenerationException.class)
                .satisfies(e -> assertThat(((AnalysisTradePlanGenerationException) e).code())
                        .isEqualTo("PROFILE_CURRENCY_MISMATCH"));
    }

    @Test
    void marketIntelligenceRejectionPropagatesUpstreamStatus() {
        FeignException rejection = mock(FeignException.class);
        when(rejection.status()).thenReturn(422);
        when(marketIntelligence.generateFromOpportunity(any(), any(), any()))
                .thenThrow(rejection);

        assertThatThrownBy(() -> service.createFromOpportunity(
                actorId, opportunityId, accountId, "key-1"))
                .isInstanceOf(AnalysisTradePlanGenerationException.class)
                .satisfies(e -> assertThat(((AnalysisTradePlanGenerationException) e).status())
                        .isEqualTo(422));
    }

    @Test
    void decideSubstitutesAuthenticatedActorAndDelegates() {
        when(marketIntelligence.decide(eq(accountId), eq(2L), any()))
                .thenReturn(planTransport("ACCEPTED", 2));

        var plan = service.decide(actorId, accountId, 2L, "ACCEPT");

        assertThat(plan.status()).isEqualTo("ACCEPTED");
        org.mockito.Mockito.verify(marketIntelligence).decide(
                eq(accountId), eq(2L),
                argThat((MarketIntelligenceTradePlanningClient.DecisionRequest sent) ->
                        sent.actorId().equals(actorId) && sent.decision().equals("ACCEPT")));
    }

    @Test
    void loadPassesActorForOwnershipCheck() {
        when(marketIntelligence.load(accountId, 2L, actorId))
                .thenReturn(planTransport("PROPOSED", 2));

        var plan = service.load(actorId, accountId, 2L);

        assertThat(plan.version()).isEqualTo(2);
    }

    private MarketIntelligenceTradePlanningClient.PlanTransport planTransport(
            String status, long version) {
        return new MarketIntelligenceTradePlanningClient.PlanTransport(
                UUID.randomUUID(), version, null, status,
                UUID.randomUUID(), 1, Instant.now(), "BTC/EUR", "LONG",
                "LIMIT", BigDecimal.valueOf(101), BigDecimal.valueOf(99),
                java.util.List.of(BigDecimal.valueOf(105)), BigDecimal.valueOf(0.05),
                BigDecimal.valueOf(5), BigDecimal.valueOf(2), BigDecimal.TEN,
                Instant.now().plusSeconds(3600), "thesis",
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
                Instant.now(), UUID.randomUUID());
    }
}
