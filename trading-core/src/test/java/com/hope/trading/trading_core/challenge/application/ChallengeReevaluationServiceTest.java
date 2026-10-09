package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.risk.audit.ContextMetadata;
import com.hope.trading.risk.audit.TraceMetadata;
import com.hope.trading.risk.domain.RiskTypes;
import com.hope.trading.risk.domain.RiskValidationResult;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.risk.application.AccountRiskMonitoringService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

class ChallengeReevaluationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    @Test
    void postSettlementEvaluationPassesChallengeFromAuthoritativeBalance() {
        UUID accountId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        User user = User.builder().userId(UUID.randomUUID()).username("owner").role(Role.ROLE_USER).build();
        Account account = Account.builder().accountId(accountId).baseCurrency("USD")
                .startingBalance(new BigDecimal("10000")).equity(new BigDecimal("11000"))
                .peakEquity(new BigDecimal("11000")).user(user).build();
        account.addBalance(AccountBalance.builder().asset("USD").amount(new BigDecimal("11000")).build());
        ChallengeDefinition definition = definition(policyId);
        ChallengeInstance challenge = ChallengeInstance.start(UUID.randomUUID(), accountId, definition,
                definition.startingCapital(), NOW);

        AccountRepository accounts = mock(AccountRepository.class);
        ChallengeInstanceRepository instances = mock(ChallengeInstanceRepository.class);
        ChallengeDefinitionRepository definitions = mock(ChallengeDefinitionRepository.class);
        AccountRiskMonitoringService monitoring = mock(AccountRiskMonitoringService.class);
        RiskValidationResult risk = risk(policyId);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(instances.findByAccountIdAndStatus(accountId, ChallengeStatus.ACTIVE)).thenReturn(Optional.of(challenge));
        when(definitions.findById(any())).thenReturn(Optional.of(definition));
        when(monitoring.evaluate(same(challenge), same(account), any()))
                .thenReturn(new AccountRiskMonitoringService.Evaluation(UUID.randomUUID(), risk, new BigDecimal("10000"), 1));

        ChallengeReevaluationService service = new ChallengeReevaluationService(accounts, definitions, instances,
                monitoring, new ChallengeEvaluator(), Clock.fixed(NOW, ZoneOffset.UTC));

        service.reevaluate(account);

        assertThat(challenge.status()).isEqualTo(ChallengeStatus.PASSED);
        verify(instances).save(challenge);
    }

    @Test
    void accountWithoutChallengeRemainsUnaffected() {
        Account account = Account.builder().accountId(UUID.randomUUID()).build();
        AccountRepository accounts = mock(AccountRepository.class);
        ChallengeInstanceRepository instances = mock(ChallengeInstanceRepository.class);
        when(instances.findByAccountIdAndStatus(account.getAccountId(), ChallengeStatus.ACTIVE))
                .thenReturn(Optional.empty());

        ChallengeReevaluationService service = new ChallengeReevaluationService(accounts,
                mock(ChallengeDefinitionRepository.class), instances,
                mock(AccountRiskMonitoringService.class), new ChallengeEvaluator(), Clock.fixed(NOW, ZoneOffset.UTC));

        service.reevaluate(account);

        verifyNoInteractions(accounts);
        verify(instances, never()).save(any());
    }

    private ChallengeDefinition definition(UUID policyId) {
        return ChallengeDefinition.create(UUID.randomUUID(), "1.0.0", "TEST", "PAPER", "PLAN_A", "USD",
                new BigDecimal("10000"), new BigDecimal("0.10"), ProgressionValueSource.BALANCE, policyId,
                "1.0.0", "https://example.test", NOW, NOW, "test", NOW);
    }

    private RiskValidationResult risk(UUID policyId) {
        RiskValidationResult result = mock(RiskValidationResult.class);
        when(result.evaluationStatus()).thenReturn(RiskTypes.EvaluationStatus.COMPLETED);
        when(result.ruleResults()).thenReturn(java.util.List.of());
        when(result.evaluatedAt()).thenReturn(NOW);
        when(result.trace()).thenReturn(new TraceMetadata(UUID.randomUUID(), UUID.randomUUID(), "test",
                Map.of(policyId.toString(), "1.0.0"), Map.of(), new ContextMetadata(UUID.randomUUID(), 1,
                UUID.randomUUID(), 1, 1, 1, NOW, NOW, NOW, NOW)));
        return result;
    }
}
