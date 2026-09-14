package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.execution.application.pipeline.ExecutionAttemptCreationStep;
import com.hope.trading.trading_core.execution.application.pipeline.ExecutionPipelineContext;
import com.hope.trading.trading_core.execution.application.port.ExecutionIdGenerator;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionAttempt;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.event.ExecutionEvent;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionAttemptRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.hope.trading.trading_core.execution.ExecutionTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionAttemptCreationStepRegressionTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Mock
    private ExecutionAttemptRepositoryPort attempts;

    @Mock
    private ExecutionIdGenerator ids;

    private ExecutionAttemptCreationStep step;

    @Test
    void executeCreatesFirstAttemptForIntent() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.t1EvaluationId(UUID.randomUUID());

        when(ids.nextAttemptId()).thenReturn(new ExecutionAttemptId(uuid(100)));
        when(attempts.findByIntentId(intent.id())).thenReturn(List.of());
        when(attempts.save(any(ExecutionAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        step = new ExecutionAttemptCreationStep(attempts, ids);

        step.execute(ctx);

        assertThat(ctx.attempt()).isNotNull();
        assertThat(ctx.attempt().attemptNumber()).isEqualTo(1);
        assertThat(ctx.attempt().intentId()).isEqualTo(intent.id());
        assertThat(ctx.attempt().status()).isEqualTo(AttemptStatus.CREATED);

        verify(attempts).save(any(ExecutionAttempt.class));
        verify(ids).nextAttemptId();
    }

    @Test
    void executeCreatesSecondAttemptWhenFirstExists() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        var existingAttempt = ExecutionAttempt.create(
                new ExecutionAttemptId(uuid(50)), intent.id(), 1, NOW, UUID.randomUUID());
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.t1EvaluationId(UUID.randomUUID());

        when(ids.nextAttemptId()).thenReturn(new ExecutionAttemptId(uuid(101)));
        when(attempts.findByIntentId(intent.id())).thenReturn(List.of(existingAttempt));
        when(attempts.save(any(ExecutionAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        step = new ExecutionAttemptCreationStep(attempts, ids);

        step.execute(ctx);

        assertThat(ctx.attempt()).isNotNull();
        assertThat(ctx.attempt().attemptNumber()).isEqualTo(2);
    }

    @Test
    void executeSetsAttemptInContext() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.t1EvaluationId(UUID.randomUUID());

        when(ids.nextAttemptId()).thenReturn(new ExecutionAttemptId(uuid(102)));
        when(attempts.findByIntentId(intent.id())).thenReturn(List.of());
        when(attempts.save(any(ExecutionAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        step = new ExecutionAttemptCreationStep(attempts, ids);

        step.execute(ctx);

        assertThat(ctx.attempt()).isNotNull();
        assertThat(ctx.attempt().intentId()).isEqualTo(intent.id());
        assertThat(ctx.attempt().t1EvaluationId()).isEqualTo(ctx.t1EvaluationId());
    }

    @Test
    void executeAddsAttemptCreatedEventToIntent() {
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        // Clear existing events (Created + Validated) to test only the new event
        intent.pullEvents();
        var ctx = new ExecutionPipelineContext(intent, NOW);
        ctx.t1EvaluationId(UUID.randomUUID());

        when(ids.nextAttemptId()).thenReturn(new ExecutionAttemptId(uuid(103)));
        when(attempts.findByIntentId(intent.id())).thenReturn(List.of());
        when(attempts.save(any(ExecutionAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        step = new ExecutionAttemptCreationStep(attempts, ids);

        step.execute(ctx);

        var events = intent.pullEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ExecutionEvent.ExecutionAttemptCreated.class);
    }

    @Test
    void executeRequiresNonNullArguments() {
        step = new ExecutionAttemptCreationStep(attempts, ids);

        assertThatThrownBy(() -> step.execute(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRequiresNonNullDependencies() {
        assertThatThrownBy(() -> new ExecutionAttemptCreationStep(null, ids))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new ExecutionAttemptCreationStep(attempts, null))
                .isInstanceOf(NullPointerException.class);
    }
}