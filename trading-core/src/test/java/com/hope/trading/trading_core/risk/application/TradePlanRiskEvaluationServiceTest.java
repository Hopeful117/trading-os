package com.hope.trading.trading_core.risk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.risk.application.RiskEvaluationModels.Command;
import com.hope.trading.trading_core.risk.application.RiskEvaluationModels.Response;
import com.hope.trading.trading_core.risk.application.port.BrokerRiskFactsPort;
import com.hope.trading.trading_core.risk.application.port.MarketValuationPort;
import com.hope.trading.trading_core.risk.application.port.RequiredMarginPort;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class TradePlanRiskEvaluationServiceTest {
    private final UUID actorId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID brokerAccountId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-08-01T12:00:00Z");
    private AccountRepository accounts;
    private BrokerAccountRepository brokerAccounts;
    private TradePlanRiskPort plans;
    private BrokerRiskFactsPort broker;
    private MarketValuationPort market;
    private RequiredMarginPort requiredMargins;
    private RiskPersistence persistence;
    private RiskAcknowledgmentDeliveryService acknowledgmentDelivery;
    private PlatformTransactionManager transactionManager;
    private TradePlanRiskEvaluationService service;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class); brokerAccounts = mock(BrokerAccountRepository.class);
        plans = mock(TradePlanRiskPort.class); broker = mock(BrokerRiskFactsPort.class);
        market = mock(MarketValuationPort.class); requiredMargins = mock(RequiredMarginPort.class);
        persistence = mock(RiskPersistence.class);
        acknowledgmentDelivery = mock(RiskAcknowledgmentDeliveryService.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        User user = User.builder().userId(actorId).username("trader").build();
        Account account = Account.builder().accountId(accountId).user(user).name("main").baseCurrency("USD").build();
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(persistence.evaluation(actorId, "key")).thenReturn(Optional.empty());
        when(persistence.write(any())).thenReturn("{}");
        AtomicLong versions = new AtomicLong();
        when(persistence.component(any(), any(), any(), any(), any())).thenAnswer(i -> versions.incrementAndGet());
        when(persistence.context(any(), any(), any())).thenAnswer(i -> versions.incrementAndGet());
        when(persistence.baseline(any(), any(), any(), any(), any(), any(), any())).thenReturn(
                new RiskPersistence.Baseline(1, new BigDecimal("10000"), "USD",
                        Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"),
                        1, "persisted-baseline"));
        service = new TradePlanRiskEvaluationService(accounts, brokerAccounts, plans, broker, market,
                requiredMargins, persistence, Clock.fixed(now, ZoneOffset.UTC), acknowledgmentDelivery,
                transactionManager);
    }

    @Test
    void missingConfigurationPersistsContextUnavailableWithoutCallingDependencies() {
        when(persistence.configuration(accountId)).thenReturn(Optional.empty());

        Response response = service.evaluate(command("key", 3));

        assertThat(response.status()).isEqualTo("CONTEXT_UNAVAILABLE");
        assertThat(response.reasons()).extracting(RiskEvaluationModels.Reason::code)
                .containsExactly("ACCOUNT_RISK_CONFIGURATION_MISSING");
        verify(plans, never()).load(any(), anyLong());
        verify(broker, never()).load(any(), any(), any());
    }

    @Test
    void missingProfileFailsClosedBeforeLoadingTradePlan() {
        when(persistence.configuration(accountId)).thenReturn(Optional.of(new RiskPersistence.AccountConfiguration(
                accountId, brokerAccountId, "UTC", "USD", UUID.randomUUID())));
        when(brokerAccounts.findByIdAndOwnerId(brokerAccountId, actorId)).thenReturn(Optional.of(
                BrokerAccount.create(actorId, BrokerProvider.KRAKEN, "Kraken", now)));
        when(persistence.assignedProfile(accountId)).thenReturn(Optional.empty());

        Response response = service.evaluate(command("key", 3));

        assertThat(response.status()).isEqualTo("CONTEXT_UNAVAILABLE");
        assertThat(response.reasons()).extracting(RiskEvaluationModels.Reason::code)
                .containsExactly("EFFECTIVE_RISK_PROFILE_MISSING");
        verify(plans, never()).load(any(), anyLong());
    }

    @Test
    void completedApprovalUsesExactVersionAndPersistsThenAttemptsAcknowledgment() {
        availableContext(List.of());

        Response response = service.evaluate(command("key", 3));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.approved()).isTrue();
        assertThat(response.trace().snapshotVersions().values()).allMatch(version -> version > 0);
        verify(plans).load(planId, 3);
        verify(persistence).acknowledgment(response.evaluationId(), planId, 3, response.decision(),
                response.evaluatedAt(), now);
        verify(acknowledgmentDelivery).deliver(response.evaluationId());
        var order = inOrder(transactionManager, acknowledgmentDelivery);
        order.verify(transactionManager).commit(any());
        order.verify(acknowledgmentDelivery).deliver(response.evaluationId());
    }

    @Test
    void missingAuthoritativeRequiredMarginFailsClosedWithoutLeverageInference() {
        availableContext(List.of());
        when(requiredMargins.resolve(any())).thenReturn(Optional.empty());

        Response response = service.evaluate(command("key", 3));

        assertThat(response.status()).isEqualTo("CONTEXT_UNAVAILABLE");
        assertThat(response.reasons()).extracting(RiskEvaluationModels.Reason::code)
                .containsExactly("REQUIRED_MARGIN_UNAVAILABLE");
    }

    @Test
    void missingPositionStopFailsClosedAndNeverAcknowledges() {
        var position = new BrokerRiskFactsPort.Position(UUID.randomUUID(), "p1", "provider", "BTCUSD",
                BigDecimal.ONE, new BigDecimal("100"), new BigDecimal("100"), BigDecimal.TEN,
                BigDecimal.ZERO, List.of());
        availableContext(List.of(position));

        Response response = service.evaluate(command("key", 3));

        assertThat(response.status()).isEqualTo("CONTEXT_UNAVAILABLE");
        assertThat(response.reasons()).extracting(RiskEvaluationModels.Reason::code)
                .containsExactly("POSITION_PROTECTION_INCOMPLETE");
        verify(persistence, never()).acknowledgment(any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    void positionLossUsesCurrentConservativePriceAndAcceptsStopBeyondEntry() {
        UUID positionId = UUID.randomUUID();
        var position = new BrokerRiskFactsPort.Position(positionId, "p1", "provider", "BTCUSD",
                BigDecimal.ONE, new BigDecimal("80"), new BigDecimal("100"), BigDecimal.TEN,
                BigDecimal.ONE, List.of(new BrokerRiskFactsPort.Stop("s1", "provider-stop",
                BigDecimal.ONE, new BigDecimal("90"))));
        availableContext(List.of(position));

        Response response = service.evaluate(command("key", 3));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.approved()).isTrue();
    }

    @Test
    void closedPnlUsesTradeSettlementAssetAtClosure() {
        availableContext(List.of());
        var trade = new BrokerRiskFactsPort.ClosedTrade("t1", "ETHEUR", "EUR",
                new BigDecimal("2"), new BigDecimal("100"), now.minusSeconds(60));
        when(broker.load(any(), any(), any())).thenReturn(brokerSnapshot(List.of(), List.of(trade)));

        Response response = service.evaluate(command("key", 3));

        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(market).value(org.mockito.ArgumentMatchers.eq("USD"),
                org.mockito.ArgumentMatchers.eq(trade.closedAt()), org.mockito.ArgumentMatchers.eq(List.of()),
                org.mockito.ArgumentMatchers.argThat(assets -> assets.size() == 2
                        && assets.stream().allMatch(asset -> "EUR".equals(asset.currency()))));
    }

    @Test
    void missingSettlementAssetFxFailsClosed() {
        availableContext(List.of());
        var trade = new BrokerRiskFactsPort.ClosedTrade("t1", "ETHJPY", "JPY",
                BigDecimal.ONE, BigDecimal.TEN, now.minusSeconds(60));
        when(broker.load(any(), any(), any())).thenReturn(brokerSnapshot(List.of(), List.of(trade)));
        doAnswer(invocation -> {
            List<MarketValuationPort.Asset> assets = invocation.getArgument(3);
            if (assets.stream().anyMatch(asset -> "pnl".equals(asset.id()))) {
                return snapshot(invocation.getArgument(1), false, List.of());
            }
            return valuation(invocation);
        }).when(market).value(any(), any(), any(), any());

        Response response = service.evaluate(command("key", 3));

        assertThat(response.status()).isEqualTo("CONTEXT_UNAVAILABLE");
        assertThat(response.reasons()).extracting(RiskEvaluationModels.Reason::code)
                .containsExactly("CLOSED_TRADE_CONVERSION_UNAVAILABLE");
    }

    @Test
    void persistedBaselineAmountOverridesNewReconstruction() {
        availableContext(List.of());
        when(persistence.baseline(any(), any(), any(), any(), any(), any(), any())).thenReturn(
                new RiskPersistence.Baseline(77, new BigDecimal("20000"), "USD",
                        Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"),
                        1, "first-writer-payload"));

        Response response = service.evaluate(command("key", 3));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.decision()).isEqualTo("REJECTED");
    }

    @Test
    void sizingCurrencyMustMatchAcceptedPlanAccountCurrencyBeforeValuation() {
        availableContext(List.of());
        when(plans.load(planId, 3)).thenReturn(plan("USD", "EUR"));

        Response response = service.evaluate(command("key", 3));

        assertThat(response.status()).isEqualTo("CONTEXT_UNAVAILABLE");
        assertThat(response.reasons()).extracting(RiskEvaluationModels.Reason::code)
                .containsExactly("TRADE_PLAN_SIZING_CURRENCY_MISMATCH");
        verify(broker, never()).load(any(), any(), any());
    }

    @Test
    void accountAndSizingCurrenciesMustBothMatchNormalizedReportingCurrencyBeforeBrokerRequest() {
        availableContext(List.of());
        when(plans.load(planId, 3)).thenReturn(plan(" eur ", "eur"));

        Response response = service.evaluate(command("key", 3));

        assertThat(response.status()).isEqualTo("CONTEXT_UNAVAILABLE");
        assertThat(response.reasons()).extracting(RiskEvaluationModels.Reason::code)
                .containsExactly("TRADE_PLAN_ACCOUNT_CURRENCY_MISMATCH");
        verify(broker, never()).load(any(), any(), any());
        verify(market, never()).value(any(), any(), any(), any());
    }

    @Test
    void acknowledgmentFailureAfterOfficialCommitDoesNotChangeStoredEvaluationResponse() {
        availableContext(List.of());
        doThrow(new IllegalStateException("claim unavailable")).when(acknowledgmentDelivery).deliver(any());

        Response response = service.evaluate(command("key", 3));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.approved()).isTrue();
        verify(persistence).evaluation(any(), any(), any(), any(), anyLong(), any(), any(), any(), any(), any(), any(), any());
        verify(persistence).acknowledgment(response.evaluationId(), planId, 3, response.decision(),
                response.evaluatedAt(), now);
    }

    @Test
    void invalidRuleVocabularyIsControlledContextUnavailable() {
        availableContext(List.of());
        RiskPersistence.Profile invalid = new RiskPersistence.Profile(UUID.randomUUID(), "1.2.0",
                "platform-policy", "4.0.0", "PLATFORM", now, "source", now, "assignment", List.of(
                rule("MAX_POSITION_RISK", "ACCOUNT", "0.02"),
                rule("MAX_EXPOSURE", "PORTFOLIO", "0.50"),
                rule("DAILY_DRAWDOWN", "ACCOUNT", "0.10")));
        when(persistence.assignedProfile(accountId)).thenReturn(Optional.of(invalid));

        Response response = service.evaluate(command("key", 3));

        assertThat(response.status()).isEqualTo("CONTEXT_UNAVAILABLE");
        assertThat(response.reasons()).extracting(RiskEvaluationModels.Reason::code)
                .containsExactly("EFFECTIVE_RISK_PROFILE_INVALID");
    }

    @Test
    void duplicateRequiredRuleVersionIsRejectedAsIncompleteEffectiveSet() {
        availableContext(List.of());
        RiskPersistence.Profile duplicate = new RiskPersistence.Profile(UUID.randomUUID(), "1.2.0",
                "platform-policy", "4.0.0", "PLATFORM", now, "source", now, "assignment", List.of(
                rule("MAX_POSITION_RISK", "POSITION", "0.02"),
                new RiskPersistence.ProfileRule("MAX_POSITION_RISK", "2.0.0", "POSITION", "BLOCKING",
                        10, new BigDecimal("0.01"), "conflicting-version"),
                rule("MAX_EXPOSURE", "PORTFOLIO", "0.50"),
                rule("DAILY_DRAWDOWN", "ACCOUNT", "0.10")));
        when(persistence.assignedProfile(accountId)).thenReturn(Optional.of(duplicate));

        Response response = service.evaluate(command("key", 3));

        assertThat(response.status()).isEqualTo("CONTEXT_UNAVAILABLE");
        assertThat(response.reasons()).extracting(RiskEvaluationModels.Reason::code)
                .containsExactly("EFFECTIVE_RISK_PROFILE_INCOMPLETE");
    }

    @Test
    void missingCurrentFxFailsClosed() {
        availableContext(List.of());
        doAnswer(invocation -> {
            List<MarketValuationPort.Instrument> instruments = invocation.getArgument(2);
            List<MarketValuationPort.Asset> assets = invocation.getArgument(3);
            if (!instruments.isEmpty()) return new MarketValuationPort.Snapshot(UUID.randomUUID(), 21,
                    "USD", invocation.getArgument(1), now, "v1", "PT30S", false, List.of(), "{}");
            return new MarketValuationPort.Snapshot(UUID.randomUUID(), 20, "USD", invocation.getArgument(1),
                    now, "v1", "PT30S", true, assets.stream().map(asset -> new MarketValuationPort.Fact("ASSET",
                    asset.id(), null, asset.currency(), null, BigDecimal.ONE, null, null,
                    "AVAILABLE", "identity")).toList(), "{}");
        }).when(market).value(any(), any(), any(), any());

        Response response = service.evaluate(command("key", 3));

        assertThat(response.status()).isEqualTo("CONTEXT_UNAVAILABLE");
        assertThat(response.reasons()).extracting(RiskEvaluationModels.Reason::code)
                .containsExactly("CURRENT_MARKET_VALUATION_UNAVAILABLE");
    }

    @Test
    void exactStoredCommandRetriesIncompleteAcknowledgmentWithoutReevaluationAndRejectsConflict() {
        Response storedResponse = new Response(UUID.randomUUID(), planId, 3, accountId, "COMPLETED",
                "APPROVED", true, List.of(), List.of(), Map.of(), now,
                new RiskEvaluationModels.Trace(UUID.randomUUID(), "v", Map.of(), Map.of(), Map.of()));
        when(persistence.evaluation(actorId, "key")).thenReturn(Optional.of(
                new RiskPersistence.StoredEvaluation(storedResponse.evaluationId(), planId, 3, accountId, storedResponse)));

        assertThat(service.evaluate(command("key", 3))).isSameAs(storedResponse);
        verify(acknowledgmentDelivery).deliver(storedResponse.evaluationId());
        assertThatThrownBy(() -> service.evaluate(command("key", 4)))
                .isInstanceOf(RiskEvaluationException.class).hasMessageContaining("another command");
        verify(accounts, never()).findById(any());
        verify(plans, never()).load(any(), anyLong());
    }

    @Test
    void failedOfficialPersistenceNeverAttemptsRemoteAcknowledgment() {
        availableContext(List.of());
        doThrow(new IllegalStateException("database commit failed")).when(persistence)
                .evaluation(any(), any(), any(), any(), anyLong(), any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.evaluate(command("key", 3)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("database commit failed");
        verify(acknowledgmentDelivery, never()).deliver(any());
        verify(plans, never()).acknowledge(any(), anyLong(), any(), any(), any());
    }

    @Test
    void localCommitFailureNeverAttemptsRemoteAcknowledgment() {
        availableContext(List.of());
        doThrow(new IllegalStateException("commit failed")).when(transactionManager).commit(any());

        assertThatThrownBy(() -> service.evaluate(command("key", 3)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("commit failed");

        verify(acknowledgmentDelivery, never()).deliver(any());
        verify(plans, never()).acknowledge(any(), anyLong(), any(), any(), any());
    }

    private void availableContext(List<BrokerRiskFactsPort.Position> positions) {
        UUID portfolioId = UUID.randomUUID();
        when(persistence.configuration(accountId)).thenReturn(Optional.of(new RiskPersistence.AccountConfiguration(
                accountId, brokerAccountId, "UTC", "USD", portfolioId)));
        when(brokerAccounts.findByIdAndOwnerId(brokerAccountId, actorId)).thenReturn(Optional.of(
                BrokerAccount.create(actorId, BrokerProvider.KRAKEN, "Kraken", now)));
        when(persistence.assignedProfile(accountId)).thenReturn(Optional.of(profile()));
        when(plans.load(planId, 3)).thenReturn(plan("USD", "USD"));
        when(broker.load(any(), any(), any())).thenReturn(brokerSnapshot(positions, List.of()));
        when(market.value(any(), any(), any(), any())).thenAnswer(this::valuation);
        when(requiredMargins.resolve(any())).thenReturn(Optional.of(new RequiredMarginPort.Fact(
                new BigDecimal("100"), "USD", "broker-margin-quote", 7, now)));
    }

    private BrokerRiskFactsPort.Snapshot brokerSnapshot(List<BrokerRiskFactsPort.Position> positions,
                                                        List<BrokerRiskFactsPort.ClosedTrade> closedTrades) {
        return new BrokerRiskFactsPort.Snapshot(brokerAccountId,
                11, now, true, List.of(), Map.of("USD", new BigDecimal("10000")),
                new BrokerRiskFactsPort.Account("USD", new BigDecimal("10000"), new BigDecimal("10000"),
                        new BigDecimal("100")), positions, closedTrades, List.of(), "{\"version\":11}");
    }

    private Object valuation(org.mockito.invocation.InvocationOnMock invocation) {
        List<MarketValuationPort.Instrument> requestedInstruments = invocation.getArgument(2);
        List<MarketValuationPort.Asset> requestedAssets = invocation.getArgument(3);
        List<MarketValuationPort.Fact> facts = new java.util.ArrayList<>();
        requestedAssets.forEach(asset -> facts.add(new MarketValuationPort.Fact("ASSET", asset.id(), null,
                asset.currency(), null, "EUR".equals(asset.currency()) ? new BigDecimal("1.2") : BigDecimal.ONE,
                null, null, "AVAILABLE", "identity")));
        requestedInstruments.forEach(instrument -> facts.add(new MarketValuationPort.Fact("INSTRUMENT",
                instrument.id(), UUID.randomUUID(), null, instrument.priceUse(), new BigDecimal("100"),
                new BigDecimal("100"), BigDecimal.ONE, "AVAILABLE", "observation")));
        return snapshot(invocation.getArgument(1), true, facts);
    }

    private MarketValuationPort.Snapshot snapshot(Instant at, boolean complete,
                                                   List<MarketValuationPort.Fact> facts) {
        return new MarketValuationPort.Snapshot(UUID.randomUUID(), 20, "USD", at, now,
                "conservative-v1", "PT30S", complete, facts, "{\"version\":20}");
    }

    private TradePlanRiskPort.Snapshot plan(String accountCurrency, String sizingCurrency) {
        return new TradePlanRiskPort.Snapshot(planId, 3, "ACCEPTED", now,
                UUID.randomUUID(), 8, now, actorId, accountId, accountCurrency,
                UUID.randomUUID(), 2, UUID.randomUUID(), 4,
                "ETHUSD", "LONG", new BigDecimal("100"), new BigDecimal("90"), BigDecimal.ONE,
                new BigDecimal("1000"), new BigDecimal("100"), sizingCurrency, "{\"accepted\":true}");
    }

    private RiskPersistence.Profile profile() {
        return new RiskPersistence.Profile(UUID.randomUUID(), "1.2.0", "platform-policy", "4.0.0",
                "PLATFORM", now, "approved-policy-source", now, "explicit-account-assignment", List.of(
                rule("MAX_POSITION_RISK", "POSITION", "0.02"),
                rule("MAX_EXPOSURE", "PORTFOLIO", "0.50"),
                rule("DAILY_DRAWDOWN", "ACCOUNT", "0.10")));
    }

    private RiskPersistence.ProfileRule rule(String id, String category, String maximum) {
        return new RiskPersistence.ProfileRule(id, "1.0.0", category, "BLOCKING", 10,
                new BigDecimal(maximum), "approved-rule-source:" + id);
    }

    private Command command(String key, long version) {
        return new Command(actorId, planId, version, accountId, key, now);
    }
}
