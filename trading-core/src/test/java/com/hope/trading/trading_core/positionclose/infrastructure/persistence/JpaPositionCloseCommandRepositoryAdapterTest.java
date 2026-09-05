package com.hope.trading.trading_core.positionclose.infrastructure.persistence;

import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseCommand;
import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaPositionCloseCommandRepositoryAdapterTest {

    @Mock
    private JpaPositionCloseCommandRepository repository;

    @InjectMocks
    private JpaPositionCloseCommandRepositoryAdapter adapter;

    private PositionCloseCommandEntity createEntity() {
        PositionCloseCommandEntity entity = new PositionCloseCommandEntity();
        entity.id = UUID.randomUUID();
        entity.accountId = UUID.randomUUID();
        entity.brokerAccountId = UUID.randomUUID();
        entity.brokerPositionReference = "txid-123";
        entity.resolvedMutationScope = entity.brokerAccountId + ":XXBTZUSD:short";
        entity.idempotencyKey = "idem-key-1";
        entity.status = PositionCloseStatus.SUBMITTED;
        entity.reconciliationResult = null;
        entity.externalOrderId = null;
        entity.failureReason = null;
        entity.createdAt = Instant.parse("2026-01-01T00:00:00Z");
        entity.updatedAt = Instant.parse("2026-01-01T00:01:00Z");
        entity.version = 1L;
        return entity;
    }

    @Test
    void findByIdDelegatesToRepository() {
        PositionCloseCommandEntity entity = createEntity();
        when(repository.findById(entity.id)).thenReturn(Optional.of(entity));

        Optional<PositionCloseCommand> result = adapter.findById(entity.id);

        assertThat(result).isPresent();
        assertThat(result.get().id).isEqualTo(entity.id);
        assertThat(result.get().brokerPositionReference).isEqualTo("txid-123");
        verify(repository).findById(entity.id);
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.findById(id)).isEmpty();
    }

    @Test
    void findByIdempotencyKeyDelegatesToRepository() {
        PositionCloseCommandEntity entity = createEntity();
        when(repository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.of(entity));

        Optional<PositionCloseCommand> result = adapter.findByIdempotencyKey("idem-key-1");

        assertThat(result).isPresent();
        assertThat(result.get().idempotencyKey).isEqualTo("idem-key-1");
    }

    @Test
    void findByAccountIdReturnsMappedList() {
        PositionCloseCommandEntity e1 = createEntity();
        PositionCloseCommandEntity e2 = createEntity();
        e2.id = UUID.randomUUID();
        when(repository.findByAccountId(e1.accountId)).thenReturn(List.of(e1, e2));

        List<PositionCloseCommand> result = adapter.findByAccountId(e1.accountId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id).isEqualTo(e1.id);
        assertThat(result.get(1).id).isEqualTo(e2.id);
    }

    @Test
    void findByAccountIdReturnsEmptyList() {
        UUID accountId = UUID.randomUUID();
        when(repository.findByAccountId(accountId)).thenReturn(List.of());

        assertThat(adapter.findByAccountId(accountId)).isEmpty();
    }

    @Test
    void findActiveByBrokerAccountAndScopeDelegatesWithStatuses() {
        UUID brokerAccountId = UUID.randomUUID();
        String scope = brokerAccountId + ":XXBTZUSD:short";
        when(repository.findActiveByBrokerAccountAndScope(eq(brokerAccountId), eq(scope)))
                .thenReturn(List.of());

        List<PositionCloseCommand> result = adapter.findActiveByBrokerAccountAndScope(brokerAccountId, scope);

        assertThat(result).isEmpty();
    }

    @Test
    void saveMapsToEntityAndBack() {
        PositionCloseCommand command = new PositionCloseCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "ref", "scope", "idem", PositionCloseStatus.CREATED,
                null, null, null, Instant.now(), Instant.now(), 0L);
        PositionCloseCommandEntity savedEntity = createEntity();
        savedEntity.id = command.id;
        when(repository.save(any())).thenReturn(savedEntity);

        PositionCloseCommand result = adapter.save(command);

        assertThat(result).isNotNull();
        assertThat(result.id).isEqualTo(command.id);
        verify(repository).save(any(PositionCloseCommandEntity.class));
    }

    @Test
    void deleteMapsToEntityAndDelegates() {
        PositionCloseCommand command = new PositionCloseCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "ref", "scope", "idem", PositionCloseStatus.CLOSED,
                null, null, null, Instant.now(), Instant.now(), 0L);
        doNothing().when(repository).delete(any());

        adapter.delete(command);

        verify(repository).delete(any(PositionCloseCommandEntity.class));
    }
}
