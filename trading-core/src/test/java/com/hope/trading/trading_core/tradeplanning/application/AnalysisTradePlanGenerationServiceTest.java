package com.hope.trading.trading_core.tradeplanning.application;

import com.hope.trading.trading_core.model.*;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile;
import com.hope.trading.trading_core.tradeplanning.infrastructure.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisTradePlanGenerationServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TradePlanningProfileService profiles = mock(TradePlanningProfileService.class);
    private final AnalysisTradePlanContinuationRepository continuations =
            mock(AnalysisTradePlanContinuationRepository.class);
    private final MarketIntelligenceTradePlanningClient client =
            mock(MarketIntelligenceTradePlanningClient.class);
    private final Instant now = Instant.parse("2026-08-01T12:00:00Z");
    private final AnalysisTradePlanGenerationService service =
            new AnalysisTradePlanGenerationService(
                    accounts, profiles, continuations, client,
                    Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void mapsExactAssignedProfileAndReusesDurableSuccessfulContinuation() {
        UUID actorId = UUID.randomUUID(); UUID accountId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID(); UUID profileId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        User user = User.builder().userId(actorId).build();
        Account account = Account.builder().accountId(accountId).user(user)
                .name("Primary").baseCurrency("USD").build();
        TradePlanningProfile profile = profile(profileId, actorId);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(profiles.effective(actorId, accountId)).thenReturn(profile);
        AtomicReference<AnalysisTradePlanContinuationEntity> persisted = new AtomicReference<>();
        when(continuations.findByAnalysisExecutionIdAndActorIdAndAccountIdAndIdempotencyKey(
                analysisId, actorId, accountId, "key-1"))
                .thenAnswer(ignored -> Optional.ofNullable(persisted.get()));
        when(continuations.saveAndFlush(any())).thenAnswer(invocation -> {
            persisted.set(invocation.getArgument(0)); return invocation.getArgument(0);
        });
        when(continuations.save(any())).thenAnswer(invocation -> {
            persisted.set(invocation.getArgument(0)); return invocation.getArgument(0);
        });
        when(client.generate(eq(analysisId), eq("key-1"), any()))
                .thenReturn(new MarketIntelligenceTradePlanningClient.Response(planId, 1));

        var first = service.generate(actorId, analysisId, accountId, "key-1");
        var replay = service.generate(actorId, analysisId, accountId, "key-1");

        assertThat(first).isEqualTo(replay);
        ArgumentCaptor<MarketIntelligenceTradePlanningClient.Request> command =
                ArgumentCaptor.forClass(MarketIntelligenceTradePlanningClient.Request.class);
        verify(client, times(1)).generate(eq(analysisId), eq("key-1"), command.capture());
        assertThat(command.getValue().actorId()).isEqualTo(actorId);
        assertThat(command.getValue().accountId()).isEqualTo(accountId);
        assertThat(command.getValue().context().accountCurrency()).isEqualTo("USD");
        assertThat(command.getValue().context().riskBudget().sourceId()).isEqualTo(profileId);
        assertThat(command.getValue().context().riskBudget().sourceVersion()).isEqualTo(3);
        assertThat(command.getValue().context().preferences().version()).isEqualTo(3);
    }

    @Test
    void rejectsAnAccountNotOwnedByThePrincipalBeforeCallingDownstream() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder().accountId(accountId)
                .user(User.builder().userId(UUID.randomUUID()).build())
                .name("Other").baseCurrency("USD").build();
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.generate(
                UUID.randomUUID(), UUID.randomUUID(), accountId, "key-1"))
                .isInstanceOf(AnalysisTradePlanGenerationException.class)
                .extracting("code").isEqualTo("ACCOUNT_FORBIDDEN");
        verifyNoInteractions(client);
    }

    private TradePlanningProfile profile(UUID id, UUID ownerId) {
        return new TradePlanningProfile(
                id, 3, ownerId,
                new TradePlanningProfile.RiskBudget(
                        new BigDecimal("250"), "USD", id, 3),
                new TradePlanningProfile.PlanningPreferences(
                        id, 3, TradePlanningProfile.EntryType.LIMIT,
                        TradePlanningProfile.StopStrategy.PERCENTAGE_DISTANCE,
                        new BigDecimal("1.5"),
                        TradePlanningProfile.TargetStrategy.RISK_MULTIPLE,
                        new BigDecimal("2"), TradePlanningProfile.PlanningHorizon.INTRADAY,
                        Duration.ofHours(2)), now.minusSeconds(60));
    }
}
