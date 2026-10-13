package com.hope.trading.trading_core.challenge.benchmark;

import com.hope.trading.risk.audit.ContextMetadata;
import com.hope.trading.risk.audit.TraceMetadata;
import com.hope.trading.risk.domain.RiskTypes;
import com.hope.trading.risk.domain.RiskValidationResult;
import com.hope.trading.risk.rule.RiskRuleResult;
import com.hope.trading.risk.rule.RuleExplanation;
import com.hope.trading.trading_core.brokeraccount.api.CreateBrokerAccountRequest;
import com.hope.trading.trading_core.brokeraccount.api.RiskProfileReference;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountService;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.challenge.application.ChallengeDefinitionRepository;
import com.hope.trading.trading_core.challenge.application.ChallengeCatalogService;
import com.hope.trading.trading_core.challenge.application.ChallengeEvaluator;
import com.hope.trading.trading_core.challenge.application.ChallengeInstanceRepository;
import com.hope.trading.trading_core.challenge.application.ChallengeInstrumentCompatibilityException;
import com.hope.trading.trading_core.challenge.application.ChallengeInstrumentCompatibilityService;
import com.hope.trading.trading_core.challenge.application.StartChallengeCommand;
import com.hope.trading.trading_core.challenge.application.StartChallengeService;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinitionKey;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountValuationStatus;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.UserRepository;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KrakenPropAdvanced50kBenchmarkTest {
    private static final UUID PROFILE_ID = UUID.fromString(
            "0a10c7e2-9d1e-4f5a-b6c8-123456789089");
    private static final UUID DEFINITION_ID = UUID.fromString(
            "00890000-0000-4000-8000-000000000050");
    private static final String VERSION = "1.0.0";
    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private BrokerAccountService brokerAccounts;
    @Autowired private RiskPersistence riskPersistence;
    @Autowired private ChallengeDefinitionRepository definitions;
    @Autowired private ChallengeCatalogService catalog;
    @Autowired private ChallengeInstanceRepository instances;
    @Autowired private StartChallengeService startChallenge;
    @Autowired private ChallengeInstrumentCompatibilityService compatibility;
    @Autowired private ChallengeEvaluator evaluator;

    @Test
    void flywayCatalogContainsExactAdvanced50kBenchmark() {
        RiskPersistence.Profile profile = riskPersistence.profile(PROFILE_ID, VERSION).orElseThrow();
        ChallengeDefinition definition = definitions.findById(
                new ChallengeDefinitionKey(DEFINITION_ID, VERSION)).orElseThrow();

        assertThat(profile.policyId()).isEqualTo("KRAKEN_PROP_ADVANCED_50K_RISK");
        assertThat(profile.semanticVersion()).isEqualTo(VERSION);
        assertThat(profile.rules()).extracting(RiskPersistence.ProfileRule::ruleId)
                .containsExactlyInAnyOrder("MAX_POSITION_RISK", "MAX_EXPOSURE",
                        "DAILY_DRAWDOWN", "MAX_TOTAL_DRAWDOWN");
        assertThat(profile.rules()).filteredOn(rule -> "DAILY_DRAWDOWN".equals(rule.ruleId()))
                .singleElement().extracting(RiskPersistence.ProfileRule::maximumRatio)
                .isEqualTo(new BigDecimal("0.030000000000"));
        assertThat(profile.rules()).filteredOn(rule -> "MAX_TOTAL_DRAWDOWN".equals(rule.ruleId()))
                .singleElement().extracting(RiskPersistence.ProfileRule::maximumRatio)
                .isEqualTo(new BigDecimal("0.030000000000"));

        assertThat(definition.provider()).isEqualTo("KRAKEN_PROP");
        assertThat(definition.product()).isEqualTo("EVALUATION");
        assertThat(definition.planCode()).isEqualTo("ADVANCED");
        assertThat(definition.capitalCurrency()).isEqualTo("USD");
        assertThat(definition.startingCapital()).isEqualByComparingTo("50000");
        assertThat(definition.profitTargetRatio()).isEqualByComparingTo("0.09");
        assertThat(definition.progressionValueSource()).isEqualTo(
                com.hope.trading.trading_core.challenge.domain.ProgressionValueSource.EQUITY);
        assertThat(definition.requiredSettlementModel()).isEqualTo(SettlementModel.CASH);
        assertThat(definition.requiredInstrumentProductType())
                .isEqualTo(InstrumentProductType.LINEAR_PERPETUAL);
        assertThat(definition.sourceUrl()).isEqualTo("https://www.kraken.com/pro/prop");
        assertThat(definition.provenance())
                .contains("https://support.kraken.com/articles/kraken-prop-faq")
                .contains("https://blog.kraken.com/product/prop/introducing-kraken-prop");
    }

    @Test
    void genericCatalogExposesTheKrakenBenchmark() {
        assertThat(catalog.selectable())
                .anySatisfy(definition -> {
                    assertThat(definition.id()).isEqualTo(DEFINITION_ID);
                    assertThat(definition.definitionVersion()).isEqualTo(VERSION);
                    assertThat(definition.provider()).isEqualTo("KRAKEN_PROP");
                });
    }

    @Test
    void provisionsCashAccountWithConfiguredRiskDayAndStartsBenchmark() {
        User user = users.save(User.builder().username("kraken-prop-benchmark")
                .password("x").email("kraken-prop-benchmark@test.local")
                .role(Role.ROLE_USER).build());

        var response = brokerAccounts.create(user.getUserId(), new CreateBrokerAccountRequest(
                BrokerProvider.KRAKEN, "Kraken Prop Advanced 50k", ExecutionMode.PAPER,
                new BigDecimal("50000"), new RiskProfileReference(PROFILE_ID, VERSION),
                SettlementModel.CASH, "UTC", "00:30"));

        UUID accountId = jdbc.queryForObject(
                "select account_id from accounts where broker_account_id=?", UUID.class, response.id());
        assertThat(jdbc.queryForObject(
                "select risk_time_zone from account_risk_configuration where account_id=?",
                String.class, accountId)).isEqualTo("UTC");
        assertThat(jdbc.queryForObject(
                "select risk_day_reset_time from account_risk_configuration where account_id=?",
                String.class, accountId)).isEqualTo("00:30");

        ChallengeInstance challenge = startChallenge.start(new StartChallengeCommand(
                user.getUserId(), accountId, DEFINITION_ID, VERSION));

        assertThat(challenge.status()).isEqualTo(ChallengeStatus.ACTIVE);
        assertThat(challenge.definitionId()).isEqualTo(DEFINITION_ID);
        assertThat(challenge.definitionVersion()).isEqualTo(VERSION);
        assertThat(challenge.riskPolicyId()).isEqualTo(PROFILE_ID);
        assertThat(challenge.riskPolicyVersion()).isEqualTo(VERSION);
        assertThat(challenge.startingCapital()).isEqualByComparingTo("50000");
        assertThat(instances.findByAccountIdAndStatus(accountId, ChallengeStatus.ACTIVE)).isPresent();
    }

    @Test
    void genericCompatibilityAcceptsLinearPerpetualAndRejectsSpot() {
        UUID accountId = UUID.randomUUID();
        ChallengeDefinition definition = definitions.findById(
                new ChallengeDefinitionKey(DEFINITION_ID, VERSION)).orElseThrow();
        ChallengeInstance challenge = ChallengeInstance.start(UUID.randomUUID(), accountId, definition,
                definition.startingCapital(), NOW);
        var instanceRepository = mock(ChallengeInstanceRepository.class);
        var definitionRepository = mock(ChallengeDefinitionRepository.class);
        when(instanceRepository.findByAccountIdAndStatus(accountId, ChallengeStatus.ACTIVE))
                .thenReturn(java.util.Optional.of(challenge));
        when(definitionRepository.findById(new ChallengeDefinitionKey(DEFINITION_ID, VERSION)))
                .thenReturn(java.util.Optional.of(definition));
        var service = new ChallengeInstrumentCompatibilityService(instanceRepository, definitionRepository);

        assertThatCode(() -> service.validate(accountId, InstrumentProductType.LINEAR_PERPETUAL))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.validate(accountId, InstrumentProductType.SPOT))
                .isInstanceOf(ChallengeInstrumentCompatibilityException.class)
                .extracting("code").isEqualTo("CHALLENGE_INSTRUMENT_PRODUCT_MISMATCH");
    }

    @Test
    void equityProgressionUsesInclusiveTargetAndRiskBreachWins() {
        ChallengeDefinition definition = definitions.findById(
                new ChallengeDefinitionKey(DEFINITION_ID, VERSION)).orElseThrow();
        Account belowTarget = account("54499.99");
        ChallengeInstance active = ChallengeInstance.start(UUID.randomUUID(), UUID.randomUUID(), definition,
                definition.startingCapital(), NOW);
        assertThat(evaluator.evaluate(active, definition, belowTarget, completedRisk(List.of()),
                UUID.randomUUID(), NOW)).isEqualTo(ChallengeStatus.ACTIVE);

        Account atTarget = account("54500.00");
        ChallengeInstance passed = ChallengeInstance.start(UUID.randomUUID(), UUID.randomUUID(), definition,
                definition.startingCapital(), NOW);
        assertThat(evaluator.evaluate(passed, definition, atTarget, completedRisk(List.of()),
                UUID.randomUUID(), NOW)).isEqualTo(ChallengeStatus.PASSED);

        ChallengeInstance breached = ChallengeInstance.start(UUID.randomUUID(), UUID.randomUUID(), definition,
                definition.startingCapital(), NOW);
        RiskRuleResult violation = new RiskRuleResult("DAILY_DRAWDOWN", "1.0.0",
                RiskTypes.RuleStatus.FAILURE, RiskTypes.RuleSeverity.BLOCKING,
                new RuleExplanation("daily-drawdown", Map.of()), Map.of(), NOW, Map.of());
        assertThat(evaluator.evaluate(breached, definition, atTarget,
                completedRisk(List.of(violation)), UUID.randomUUID(), NOW))
                 .isEqualTo(ChallengeStatus.BREACHED);
    }

    @Test
    void staleOrUnavailableEquityCannotPassBenchmark() {
        ChallengeDefinition definition = definitions.findById(
                new ChallengeDefinitionKey(DEFINITION_ID, VERSION)).orElseThrow();

        for (AccountValuationStatus status : List.of(AccountValuationStatus.STALE,
                AccountValuationStatus.UNAVAILABLE)) {
            Account account = account("54500.00");
            account.setValuationStatus(status);
            account.setValuedAt(NOW);
            ChallengeInstance challenge = ChallengeInstance.start(UUID.randomUUID(), UUID.randomUUID(),
                    definition, definition.startingCapital(), NOW);

            assertThat(evaluator.evaluate(challenge, definition, account, completedRisk(List.of()),
                    UUID.randomUUID(), NOW)).isEqualTo(ChallengeStatus.ACTIVE);
        }
    }

    @Test
    void configuredRiskDayRollsOverAtUtc0030() {
        com.hope.trading.trading_core.risk.application.RiskDay beforeReset =
                com.hope.trading.trading_core.risk.application.RiskDay.containing(
                        Instant.parse("2026-10-13T00:29:59Z"), "UTC", java.time.LocalTime.parse("00:30"));
        com.hope.trading.trading_core.risk.application.RiskDay afterReset =
                com.hope.trading.trading_core.risk.application.RiskDay.containing(
                        Instant.parse("2026-10-13T00:30:00Z"), "UTC", java.time.LocalTime.parse("00:30"));

        assertThat(beforeReset.date()).isEqualTo(java.time.LocalDate.parse("2026-10-12"));
        assertThat(afterReset.date()).isEqualTo(java.time.LocalDate.parse("2026-10-13"));
        assertThat(beforeReset.startsAt()).isNotEqualTo(afterReset.startsAt());
    }

    private Account account(String equity) {
        User user = User.builder().userId(UUID.randomUUID()).username("benchmark-owner")
                .role(Role.ROLE_USER).build();
        Account account = Account.builder().accountId(UUID.randomUUID()).baseCurrency("USD")
                .settlementModel(SettlementModel.CASH).startingBalance(new BigDecimal("50000"))
                .equity(new BigDecimal(equity)).realizedBalance(new BigDecimal("50000"))
                .valuationStatus(AccountValuationStatus.FRESH).valuedAt(NOW)
                .peakEquity(new BigDecimal(equity)).user(user).build();
        return account;
    }

    private RiskValidationResult completedRisk(List<RiskRuleResult> rules) {
        RiskValidationResult result = mock(RiskValidationResult.class);
        TraceMetadata trace = new TraceMetadata(UUID.randomUUID(), UUID.randomUUID(), "test",
                Map.of(PROFILE_ID.toString(), VERSION), Map.of(), new ContextMetadata(
                UUID.randomUUID(), 1, UUID.randomUUID(), 1, 1, 1, NOW, NOW, NOW, NOW));
        when(result.evaluationStatus()).thenReturn(RiskTypes.EvaluationStatus.COMPLETED);
        when(result.ruleResults()).thenReturn(rules);
        when(result.trace()).thenReturn(trace);
        return result;
    }
}
