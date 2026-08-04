package com.hope.trading.trading_core.tradeplanning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TradePlanningProfileServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void missingAssignmentFailsClosedForTheAccountOwner() {
        UUID owner = UUID.randomUUID(); UUID accountId = UUID.randomUUID();
        AccountRepository accounts = mock(AccountRepository.class);
        TradePlanningProfileRepository profiles = mock(TradePlanningProfileRepository.class);
        when(accounts.findById(accountId)).thenReturn(Optional.of(Account.builder().accountId(accountId)
                .user(User.builder().userId(owner).build()).name("main").baseCurrency("USD").build()));
        when(profiles.findAssigned(accountId)).thenReturn(Optional.empty());
        var service = new TradePlanningProfileService(accounts, profiles, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.effective(owner, accountId))
                .isInstanceOf(TradePlanningProfileException.class)
                .extracting(error -> ((TradePlanningProfileException) error).code())
                .isEqualTo("PLANNING_PROFILE_MISSING");
    }

    @Test
    void createdProfileUsesItsOwnImmutableVersionAsTypedPlanningProvenance() {
        AccountRepository accounts = mock(AccountRepository.class);
        TradePlanningProfileRepository profiles = mock(TradePlanningProfileRepository.class);
        when(profiles.append(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new TradePlanningProfileService(accounts, profiles, Clock.fixed(NOW, ZoneOffset.UTC));

        TradePlanningProfile profile = service.create(UUID.randomUUID(), values());

        assertThat(profile.version()).isEqualTo(1);
        assertThat(profile.riskBudget().sourceId()).isEqualTo(profile.id());
        assertThat(profile.riskBudget().sourceVersion()).isEqualTo(profile.version());
        assertThat(profile.preferences().id()).isEqualTo(profile.id());
        assertThat(profile.preferences().version()).isEqualTo(profile.version());
    }

    private TradePlanningProfileService.Values values() {
        return new TradePlanningProfileService.Values(new BigDecimal("100"), "usd",
                TradePlanningProfile.EntryType.LIMIT, TradePlanningProfile.StopStrategy.PERCENTAGE_DISTANCE,
                BigDecimal.ONE, TradePlanningProfile.TargetStrategy.RISK_MULTIPLE, new BigDecimal("2"),
                TradePlanningProfile.PlanningHorizon.INTRADAY, Duration.ofHours(1));
    }
}
