package com.hope.trading.trading_core.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.execution.application.command.CreateExecutionIntentCommand;
import com.hope.trading.trading_core.execution.application.command.ValidateAndCreateCommand;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.model.*;
import com.hope.trading.trading_core.execution.domain.service.ExecutionLifecycleService;
import com.hope.trading.trading_core.execution.domain.exception.ExecutionValidationException;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.risk.application.RiskEvaluationModels;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.shared.domain.model.EntryIntent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValidateAndCreateServiceTest {

    private RiskPersistence riskPersistence;
    private TradePlanRiskPort tradePlans;
    private BrokerAccountRepository brokerAccounts;
    private CreateExecutionIntentService intentCreation;
    private ExecutionLifecycleService lifecycle;
    private ValidateAndCreateService service;

    private final UUID initiatorId = UUID.randomUUID();
    private final UUID tradePlanId = UUID.randomUUID();
    private final UUID evaluationId = UUID.randomUUID();
    private final UUID brokerAccountId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-08-07T12:00:00Z");
    private final Instant expiresAt = now.plusSeconds(3600);

    @BeforeEach
    void setUp() {
        riskPersistence = mock(RiskPersistence.class);
        tradePlans = mock(TradePlanRiskPort.class);
        brokerAccounts = mock(BrokerAccountRepository.class);
        intentCreation = mock(CreateExecutionIntentService.class);
        lifecycle = mock(ExecutionLifecycleService.class);
        service = new ValidateAndCreateService(riskPersistence, tradePlans, brokerAccounts,
                intentCreation, lifecycle, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void happyPath_createsValidatedIntent() {
        RiskPersistence.StoredEvaluation evaluation = storedEvaluation("COMPLETED", "APPROVED");
        TradePlanRiskPort.Snapshot plan = marketPlan();

        BrokerAccount brokerAccount = mock(BrokerAccount.class);
        when(brokerAccount.ownerId()).thenReturn(initiatorId);

        ExecutionIntent intent = mock(ExecutionIntent.class);

        when(riskPersistence.evaluationById(evaluationId)).thenReturn(Optional.of(evaluation));
        when(tradePlans.load(tradePlanId, 3)).thenReturn(plan);
        when(brokerAccounts.findByIdAndOwnerId(brokerAccountId, initiatorId))
                .thenReturn(Optional.of(brokerAccount));
        when(intentCreation.create(any(CreateExecutionIntentCommand.class))).thenReturn(intent);

        ExecutionIntent result = service.validateAndCreate(command());

        assertThat(result).isNotNull();
        verify(lifecycle).validate(intent, now);
    }

    @Test
    void evaluationNotFound_throws404() {
        when(riskPersistence.evaluationById(evaluationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateAndCreate(command()))
                .isInstanceOf(ExecutionValidationException.class)
                .satisfies(e -> {
                    var ve = (ExecutionValidationException) e;
                    assertThat(ve.code()).isEqualTo("EVALUATION_NOT_FOUND");
                    assertThat(ve.status()).isEqualTo(404);
                });
    }

    @Test
    void evaluationNotCompleted_throws422() {
        when(riskPersistence.evaluationById(evaluationId))
                .thenReturn(Optional.of(storedEvaluation("PENDING", "APPROVED")));

        assertThatThrownBy(() -> service.validateAndCreate(command()))
                .isInstanceOf(ExecutionValidationException.class)
                .satisfies(e -> {
                    var ve = (ExecutionValidationException) e;
                    assertThat(ve.code()).isEqualTo("EVALUATION_NOT_COMPLETED");
                });
    }

    @Test
    void decisionNotAuthorized_throws422() {
        when(riskPersistence.evaluationById(evaluationId))
                .thenReturn(Optional.of(storedEvaluation("COMPLETED", "REJECTED")));

        assertThatThrownBy(() -> service.validateAndCreate(command()))
                .isInstanceOf(ExecutionValidationException.class)
                .satisfies(e -> {
                    var ve = (ExecutionValidationException) e;
                    assertThat(ve.code()).isEqualTo("DECISION_NOT_AUTHORIZED");
                });
    }

    @Test
    void evaluationAccountMismatch_throws409() {
        RiskPersistence.StoredEvaluation evaluation = new RiskPersistence.StoredEvaluation(
                evaluationId, tradePlanId, 3, UUID.randomUUID(), "COMPLETED", "APPROVED",
                riskResponse());

        when(riskPersistence.evaluationById(evaluationId)).thenReturn(Optional.of(evaluation));

        assertThatThrownBy(() -> service.validateAndCreate(command()))
                .isInstanceOf(ExecutionValidationException.class)
                .satisfies(e -> {
                    var ve = (ExecutionValidationException) e;
                    assertThat(ve.code()).isEqualTo("EVALUATION_ACCOUNT_MISMATCH");
                });
    }

    @Test
    void brokerAccountForbidden_throws403() {
        when(riskPersistence.evaluationById(evaluationId))
                .thenReturn(Optional.of(storedEvaluation("COMPLETED", "APPROVED")));
        when(tradePlans.load(tradePlanId, 3)).thenReturn(marketPlan());
        when(brokerAccounts.findByIdAndOwnerId(brokerAccountId, initiatorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateAndCreate(command()))
                .isInstanceOf(ExecutionValidationException.class)
                .satisfies(e -> {
                    var ve = (ExecutionValidationException) e;
                    assertThat(ve.code()).isEqualTo("BROKER_ACCOUNT_FORBIDDEN");
                });
    }

    @Test
    void limitEntryIntent_requiresPrice() {
        assertThatThrownBy(() -> new EntryIntent(EntryIntent.OrderType.LIMIT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limitEntryIntent_withPrice_succeeds() {
        EntryIntent intent = new EntryIntent(EntryIntent.OrderType.LIMIT, new BigDecimal("50000"));
        assertThat(intent.orderType()).isEqualTo(EntryIntent.OrderType.LIMIT);
        assertThat(intent.price()).isEqualByComparingTo(new BigDecimal("50000"));
    }

    @Test
    void stopEntryIntent_rejectedDuringDerivation() {
        RiskPersistence.StoredEvaluation evaluation = storedEvaluation("COMPLETED", "APPROVED");
        TradePlanRiskPort.Snapshot plan = stopPlan();

        BrokerAccount brokerAccount = mock(BrokerAccount.class);
        when(brokerAccount.ownerId()).thenReturn(initiatorId);

        when(riskPersistence.evaluationById(evaluationId)).thenReturn(Optional.of(evaluation));
        when(tradePlans.load(tradePlanId, 3)).thenReturn(plan);
        when(brokerAccounts.findByIdAndOwnerId(brokerAccountId, initiatorId))
                .thenReturn(Optional.of(brokerAccount));

        assertThatThrownBy(() -> service.validateAndCreate(command()))
                .isInstanceOf(ExecutionValidationException.class)
                .satisfies(e -> {
                    var ve = (ExecutionValidationException) e;
                    assertThat(ve.code()).isEqualTo("UNSUPPORTED_ENTRY_INTENT");
                });
    }

    // --- helpers ---

    private ValidateAndCreateCommand command() {
        return new ValidateAndCreateCommand(
                initiatorId, tradePlanId, 3, evaluationId, brokerAccountId,
                new IdempotencyKey("key-1"), expiresAt);
    }

    private RiskPersistence.StoredEvaluation storedEvaluation(String status, String decision) {
        return new RiskPersistence.StoredEvaluation(
                evaluationId, tradePlanId, 3, brokerAccountId, status, decision, riskResponse());
    }

    private RiskEvaluationModels.Response riskResponse() {
        return new RiskEvaluationModels.Response(
                evaluationId, tradePlanId, 3, brokerAccountId, "COMPLETED", "APPROVED",
                true, List.of(), List.of(), Map.of(), now, null);
    }

    private TradePlanRiskPort.Snapshot marketPlan() {
        EntryIntent entryIntent = new EntryIntent(EntryIntent.OrderType.MARKET, null);
        return new TradePlanRiskPort.Snapshot(
                tradePlanId, 3, "ACCEPTED", now,
                UUID.randomUUID(), 1, now, initiatorId, brokerAccountId, "USD",
                UUID.randomUUID(), 1, UUID.randomUUID(), 1,
                "ETHUSD", "LONG", entryIntent, new BigDecimal("90"),
                BigDecimal.ONE, new BigDecimal("1000"), new BigDecimal("100"),
                "USD", "{}");
    }

    private TradePlanRiskPort.Snapshot stopPlan() {
        EntryIntent entryIntent = new EntryIntent(EntryIntent.OrderType.STOP, new BigDecimal("49000"));
        return new TradePlanRiskPort.Snapshot(
                tradePlanId, 3, "ACCEPTED", now,
                UUID.randomUUID(), 1, now, initiatorId, brokerAccountId, "USD",
                UUID.randomUUID(), 1, UUID.randomUUID(), 1,
                "BTCUSD", "LONG", entryIntent, new BigDecimal("48000"),
                BigDecimal.ONE, new BigDecimal("50000"), new BigDecimal("1000"),
                "USD", "{}");
    }
}
