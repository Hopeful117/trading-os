package com.hope.trading.trading_core.brokeraccount.application;

import com.hope.trading.trading_core.helper.AccountMapper;
import com.hope.trading.trading_core.model.Rules;
import com.hope.trading.trading_core.risk.application.RiskProfileValidator;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.RulesRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import com.hope.trading.trading_core.tradeplanning.application.TradePlanningProfileService;
import com.hope.trading.risk.domain.RiskTypes;
import com.hope.trading.risk.policy.EffectiveRiskRuleSet;
import com.hope.trading.risk.policy.RuleConfiguration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerAccountServiceTest {
    @Test
    void derivesPaperRiskBudgetFromPositionRiskAndExposureLimits() {
        EffectiveRiskRuleSet riskProfile = new EffectiveRiskRuleSet(java.util.List.of(
                rule("MAX_POSITION_RISK", "0.01", RiskTypes.RuleCategory.POSITION),
                rule("MAX_EXPOSURE", "0.03", RiskTypes.RuleCategory.PORTFOLIO),
                rule("DAILY_DRAWDOWN", "0.05", RiskTypes.RuleCategory.ACCOUNT)),
                java.util.Map.of("paper-policy", "1.0.0"));

        assertThat(BrokerAccountService.compatiblePaperRiskBudget(
                new BigDecimal("10000"), riskProfile, BigDecimal.ONE))
                .isEqualByComparingTo("3");
    }

    @Test
    void refusesCrossUserAccessWithoutRevealingAccountData() {
        BrokerAccountRepository repository = mock(BrokerAccountRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        RulesRepository rulesRepository = mock(RulesRepository.class);
        com.hope.trading.trading_core.helper.AccountMapper accountMapper = mock(AccountMapper.class);

        UUID accountId = UUID.randomUUID();
        when(repository.existsById(accountId)).thenReturn(true);
        when(repository.findByIdAndOwnerId(accountId, UUID.fromString("00000000-0000-0000-0000-000000000002")))
                .thenReturn(Optional.empty());

        BrokerAccountService service = new BrokerAccountService(
                repository,
                accountRepository,
                rulesRepository,
                mock(UserRepository.class),
                mock(AccountMapper.class),
                Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC),
                mock(RiskPersistence.class), mock(RiskProfileValidator.class),
                mock(TradePlanningProfileService.class)
        );

        assertThrows(BrokerAccountOwnershipException.class,
                () -> service.get(UUID.fromString("00000000-0000-0000-0000-000000000002"), accountId));
    }

    private static RuleConfiguration rule(String id, String ratio, RiskTypes.RuleCategory category) {
        return new RuleConfiguration(id, "1.0.0", category, RiskTypes.RuleSeverity.BLOCKING,
                10, java.util.Map.of("maximumRatio", new BigDecimal(ratio)));
    }
}
