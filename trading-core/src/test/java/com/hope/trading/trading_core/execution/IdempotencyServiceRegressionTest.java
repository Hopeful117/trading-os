package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.execution.application.pipeline.IdempotencyVerificationStep;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.exception.DuplicateExecutionException;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.service.IdempotencyService;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.hope.trading.trading_core.execution.ExecutionTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceRegressionTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Mock
    private ExecutionIntentRepositoryPort repository;

    private IdempotencyService service;

    @Test
    void ensureUniqueThrowsWhenKeyExists() {
        IdempotencyKey key = new IdempotencyKey("existing-key");
        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(intent(ExecutionStatus.CREATED)));

        service = new IdempotencyService();

        assertThatThrownBy(() -> service.ensureUnique(key, repository))
                .isInstanceOf(DuplicateExecutionException.class);
    }

    @Test
    void ensureUniqueSucceedsWhenKeyDoesNotExist() {
        IdempotencyKey key = new IdempotencyKey("new-key");
        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        service = new IdempotencyService();

        // Should not throw
        service.ensureUnique(key, repository);
    }

    @Test
    void ensureUniqueRequiresNonNullKey() {
        service = new IdempotencyService();

        assertThatThrownBy(() -> service.ensureUnique(null, repository))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void verifyIdentityThrowsWhenKeysDiffer() {
        IdempotencyKey expected = new IdempotencyKey("expected");
        // Create an intent with a different key than expected
        ExecutionIntent intent = ExecutionIntent.create(
                new ExecutionIntentId(uuid(10)),
                new com.hope.trading.trading_core.execution.domain.model.TradePlanReference(uuid(3), 1),
                new com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference(
                        uuid(4), com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference.Decision.APPROVED, NOW.minusSeconds(2)),
                new IdempotencyKey("actual"), // intent's key is "actual"
                uuid(1), uuid(2),
                new com.hope.trading.trading_core.execution.domain.model.ExecutionParameters(
                        "EURUSD",
                        com.hope.trading.trading_core.execution.domain.model.ExecutionParameters.Side.BUY,
                        com.hope.trading.trading_core.execution.domain.model.ExecutionParameters.OrderType.MARKET,
                        java.math.BigDecimal.ONE, null),
                NOW, NOW.plusSeconds(3600)
        );

        service = new IdempotencyService();

        assertThatThrownBy(() -> service.verifyIdentity(intent, expected))
                .isInstanceOf(DuplicateExecutionException.class);
    }

    @Test
    void verifyIdentityThrowsWhenActiveAttemptExists() {
        IdempotencyKey key = new IdempotencyKey("plan-3-v1"); // matches the default key from intent()
        ExecutionIntent intent = intent(ExecutionStatus.VALIDATED);
        intent.activateAttempt(new ExecutionAttemptId(uuid(20)), NOW);

        service = new IdempotencyService();

        assertThatThrownBy(() -> service.verifyIdentity(intent, key))
                .isInstanceOf(DuplicateExecutionException.class);
    }

    @Test
    void verifyIdentitySucceedsWhenKeysMatchAndNoActiveAttempt() {
        IdempotencyKey key = new IdempotencyKey("plan-3-v1"); // matches the default key from intent()
        ExecutionIntent intent = intent(ExecutionStatus.CREATED);
        // Created intent with matching key

        service = new IdempotencyService();

        // Should not throw
        service.verifyIdentity(intent, key);
    }

    @Test
    void verifyIdentityRequiresNonNullIntent() {
        service = new IdempotencyService();

        assertThatThrownBy(() -> service.verifyIdentity(null, new IdempotencyKey("key")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void verifyIdentityThrowsWhenExpectedKeyIsNull() {
        service = new IdempotencyService();

        // When expected key is null, the keys don't match (String.equals(null) returns false)
        ExecutionIntent intent = intent(ExecutionStatus.CREATED);
        assertThatThrownBy(() -> service.verifyIdentity(intent, null))
                .isInstanceOf(DuplicateExecutionException.class);
    }
}