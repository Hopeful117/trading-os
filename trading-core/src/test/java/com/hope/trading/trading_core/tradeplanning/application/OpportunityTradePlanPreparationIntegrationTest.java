package com.hope.trading.trading_core.tradeplanning.application;

import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile;
import com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OpportunityTradePlanPreparationIntegrationTest {
    private final UUID actorId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID opportunityId = UUID.randomUUID();

    @Autowired private OpportunityTradePlanOrchestrationService service;
    @Autowired private ExecutionIntentRepositoryPort executionIntents;

    @MockitoBean private AccountRepository accounts;
    @MockitoBean private TradePlanningProfileService profiles;
    @MockitoBean private MarketIntelligenceTradePlanningClient marketIntelligence;

    @BeforeEach
    void setUp() {
        when(accounts.findById(accountId)).thenReturn(Optional.of(account()));
        when(profiles.effective(actorId, accountId)).thenReturn(profile());
        when(marketIntelligence.generateFromOpportunity(eq(opportunityId), eq("integration-key"), any()))
                .thenReturn(new MarketIntelligenceTradePlanningClient.Response(UUID.randomUUID(), 1));
    }

    @Test
    void preparationDoesNotPersistAnExecutionIntent() {
        Set<UUID> intentIdsBefore = intentIds();

        service.createFromOpportunity(actorId, opportunityId, accountId, "integration-key");

        assertThat(intentIds()).containsExactlyInAnyOrderElementsOf(intentIdsBefore);
    }

    private Set<UUID> intentIds() {
        return executionIntents.findAll().stream()
                .map(intent -> intent.id().value())
                .collect(Collectors.toSet());
    }

    private Account account() {
        Account account = new Account();
        User user = new User();
        user.setUserId(actorId);
        account.setUser(user);
        account.setBaseCurrency("EUR");
        return account;
    }

    private TradePlanningProfile profile() {
        UUID profileId = UUID.randomUUID();
        return new TradePlanningProfile(profileId, 1, actorId,
                new TradePlanningProfile.RiskBudget(BigDecimal.TEN, "EUR", profileId, 1),
                new TradePlanningProfile.PlanningPreferences(profileId, 1,
                        TradePlanningProfile.EntryType.LIMIT,
                        TradePlanningProfile.StopStrategy.PERCENTAGE_DISTANCE,
                        BigDecimal.ONE,
                        TradePlanningProfile.TargetStrategy.RISK_MULTIPLE,
                        BigDecimal.valueOf(2),
                        TradePlanningProfile.PlanningHorizon.INTRADAY,
                        Duration.ofHours(1)),
                Instant.now());
    }
}
