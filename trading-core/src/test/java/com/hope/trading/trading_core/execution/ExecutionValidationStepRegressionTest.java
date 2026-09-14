package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.execution.application.pipeline.ExecutionPipelineContext;
import com.hope.trading.trading_core.execution.application.pipeline.ExecutionValidationStep;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.exception.ExecutionExpiredException;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.service.ExecutionLifecycleService;
import com.hope.trading.trading_core.execution.domain.service.ExecutionValidationService;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static com.hope.trading.trading_core.execution.ExecutionTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionValidationStepRegressionTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    private final ExecutionLifecycleService lifecycle = new ExecutionLifecycleService();

    private ExecutionValidationStep step;

    @Test
    void executeValidatesCreatedIntentAndTransitionsToValidated() {
        ExecutionIntent intent = intent(ExecutionStatus.CREATED);
        var ctx = new ExecutionPipelineContext(intent, NOW);

        step = new ExecutionValidationStep(new ExecutionValidationService(), lifecycle);

        step.execute(ctx);

        assertThat(intent.status()).isEqualTo(ExecutionStatus.VALIDATED);
    }

    @Test
    void executeValidatesAlreadyValidatedIntentWithoutRevalidating() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        var ctx = new ExecutionPipelineContext(intent, NOW);

        step = new ExecutionValidationStep(new ExecutionValidationService(), lifecycle);

        step.execute(ctx);

        // VALIDATED intent should not be re-validated (no exception thrown)
        assertThat(intent.status()).isEqualTo(ExecutionStatus.VALIDATED);
    }

    @Test
    void executeThrowsWhenIntentIsExpired() {
        var futureClock = java.time.Clock.fixed(NOW.plusSeconds(600), java.time.ZoneOffset.UTC);
        ExecutionIntent expiredIntent = ExecutionIntent.create(
                new ExecutionIntentId(uuid(50)),
                new com.hope.trading.trading_core.execution.domain.model.TradePlanReference(uuid(3), 1),
                new com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference(
                        uuid(4), com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference.Decision.APPROVED, NOW.minusSeconds(2)),
                new IdempotencyKey("expired-key"),
                uuid(1), uuid(2),
                new com.hope.trading.trading_core.execution.domain.model.ExecutionParameters(
                        "EURUSD",
                        com.hope.trading.trading_core.execution.domain.model.ExecutionParameters.Side.BUY,
                        com.hope.trading.trading_core.execution.domain.model.ExecutionParameters.OrderType.MARKET,
                        java.math.BigDecimal.ONE, null),
                NOW.minusSeconds(600),
                NOW.minusSeconds(300) // expiresAt is before NOW
        );
        var ctx = new ExecutionPipelineContext(expiredIntent, futureClock.instant());

        step = new ExecutionValidationStep(new ExecutionValidationService(), lifecycle);

        assertThatThrownBy(() -> step.execute(ctx))
                .isInstanceOf(ExecutionExpiredException.class);
    }

    @Test
    void executeThrowsWhenIntentIsTerminal() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        intent.transition(ExecutionStatus.SUBMISSION_IN_PROGRESS, NOW);
        intent.transition(ExecutionStatus.COMPLETED, NOW);
        var ctx = new ExecutionPipelineContext(intent, NOW);

        step = new ExecutionValidationStep(new ExecutionValidationService(), lifecycle);

        assertThatThrownBy(() -> step.execute(ctx))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("Terminal");
    }

    @Test
    void executeThrowsWhenIntentIsInInvalidState() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        intent.transition(ExecutionStatus.SUBMISSION_IN_PROGRESS, NOW);
        var ctx = new ExecutionPipelineContext(intent, NOW);

        step = new ExecutionValidationStep(new ExecutionValidationService(), lifecycle);

        assertThatThrownBy(() -> step.execute(ctx))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("Execution cannot start from");
    }

    @Test
    void executeRequiresNonNullArguments() {
        step = new ExecutionValidationStep(new ExecutionValidationService(), lifecycle);

        assertThatThrownBy(() -> step.execute(null))
                .isInstanceOf(NullPointerException.class);
    }
}