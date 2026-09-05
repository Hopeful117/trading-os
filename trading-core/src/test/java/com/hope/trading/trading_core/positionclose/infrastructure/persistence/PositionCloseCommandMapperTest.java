package com.hope.trading.trading_core.positionclose.infrastructure.persistence;

import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseCommand;
import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseStatus;
import com.hope.trading.trading_core.positionclose.domain.model.ReconciliationCloseResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PositionCloseCommandMapperTest {

    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");
    private static final Instant THEN = Instant.parse("2026-09-05T13:00:00Z");

    @Test
    void toDomain_null_returnsNull() {
        assertThat(PositionCloseCommandMapper.toDomain(null)).isNull();
    }

    @Test
    void toEntity_null_returnsNull() {
        assertThat(PositionCloseCommandMapper.toEntity(null)).isNull();
    }

    @Test
    void toDomain_toEntity_roundtrip_preservesAllFields() {
        PositionCloseCommandEntity entity = buildEntity();

        PositionCloseCommand domain = PositionCloseCommandMapper.toDomain(entity);
        PositionCloseCommandEntity roundTripped = PositionCloseCommandMapper.toEntity(domain);

        assertThat(roundTripped.id).isEqualTo(entity.id);
        assertThat(roundTripped.accountId).isEqualTo(entity.accountId);
        assertThat(roundTripped.brokerAccountId).isEqualTo(entity.brokerAccountId);
        assertThat(roundTripped.brokerPositionReference).isEqualTo(entity.brokerPositionReference);
        assertThat(roundTripped.resolvedMutationScope).isEqualTo(entity.resolvedMutationScope);
        assertThat(roundTripped.idempotencyKey).isEqualTo(entity.idempotencyKey);
        assertThat(roundTripped.status).isEqualTo(entity.status);
        assertThat(roundTripped.reconciliationResult).isEqualTo(entity.reconciliationResult);
        assertThat(roundTripped.externalOrderId).isEqualTo(entity.externalOrderId);
        assertThat(roundTripped.failureReason).isEqualTo(entity.failureReason);
        assertThat(roundTripped.createdAt).isEqualTo(entity.createdAt);
        assertThat(roundTripped.updatedAt).isEqualTo(entity.updatedAt);
        assertThat(roundTripped.version).isEqualTo(entity.version);
    }

    @Test
    void toEntity_toDomain_roundtrip_preservesAllFields() {
        PositionCloseCommand domain = buildDomain();

        PositionCloseCommandEntity entity = PositionCloseCommandMapper.toEntity(domain);
        PositionCloseCommand roundTripped = PositionCloseCommandMapper.toDomain(entity);

        assertThat(roundTripped.id).isEqualTo(domain.id);
        assertThat(roundTripped.accountId).isEqualTo(domain.accountId);
        assertThat(roundTripped.brokerAccountId).isEqualTo(domain.brokerAccountId);
        assertThat(roundTripped.brokerPositionReference).isEqualTo(domain.brokerPositionReference);
        assertThat(roundTripped.resolvedMutationScope).isEqualTo(domain.resolvedMutationScope);
        assertThat(roundTripped.idempotencyKey).isEqualTo(domain.idempotencyKey);
        assertThat(roundTripped.status).isEqualTo(domain.status);
        assertThat(roundTripped.reconciliationResult).isEqualTo(domain.reconciliationResult);
        assertThat(roundTripped.externalOrderId).isEqualTo(domain.externalOrderId);
        assertThat(roundTripped.failureReason).isEqualTo(domain.failureReason);
        assertThat(roundTripped.createdAt).isEqualTo(domain.createdAt);
        assertThat(roundTripped.updatedAt).isEqualTo(domain.updatedAt);
        assertThat(roundTripped.version).isEqualTo(domain.version);
    }

    @Test
    void toDomain_mapsAllFieldsFromEntity() {
        PositionCloseCommandEntity entity = buildEntity();

        PositionCloseCommand domain = PositionCloseCommandMapper.toDomain(entity);

        assertThat(domain.id).isEqualTo(entity.id);
        assertThat(domain.accountId).isEqualTo(entity.accountId);
        assertThat(domain.brokerAccountId).isEqualTo(entity.brokerAccountId);
        assertThat(domain.brokerPositionReference).isEqualTo(entity.brokerPositionReference);
        assertThat(domain.resolvedMutationScope).isEqualTo(entity.resolvedMutationScope);
        assertThat(domain.idempotencyKey).isEqualTo(entity.idempotencyKey);
        assertThat(domain.status).isEqualTo(entity.status);
        assertThat(domain.reconciliationResult).isEqualTo(entity.reconciliationResult);
        assertThat(domain.externalOrderId).isEqualTo(entity.externalOrderId);
        assertThat(domain.failureReason).isEqualTo(entity.failureReason);
        assertThat(domain.createdAt).isEqualTo(entity.createdAt);
        assertThat(domain.updatedAt).isEqualTo(entity.updatedAt);
        assertThat(domain.version).isEqualTo(entity.version);
    }

    @Test
    void toEntity_mapsAllFieldsFromDomain() {
        PositionCloseCommand domain = buildDomain();

        PositionCloseCommandEntity entity = PositionCloseCommandMapper.toEntity(domain);

        assertThat(entity.id).isEqualTo(domain.id);
        assertThat(entity.accountId).isEqualTo(domain.accountId);
        assertThat(entity.brokerAccountId).isEqualTo(domain.brokerAccountId);
        assertThat(entity.brokerPositionReference).isEqualTo(domain.brokerPositionReference);
        assertThat(entity.resolvedMutationScope).isEqualTo(domain.resolvedMutationScope);
        assertThat(entity.idempotencyKey).isEqualTo(domain.idempotencyKey);
        assertThat(entity.status).isEqualTo(domain.status);
        assertThat(entity.reconciliationResult).isEqualTo(domain.reconciliationResult);
        assertThat(entity.externalOrderId).isEqualTo(domain.externalOrderId);
        assertThat(entity.failureReason).isEqualTo(domain.failureReason);
        assertThat(entity.createdAt).isEqualTo(domain.createdAt);
        assertThat(entity.updatedAt).isEqualTo(domain.updatedAt);
        assertThat(entity.version).isEqualTo(domain.version);
    }

    @Test
    void toDomain_handlesNullOptionalFields() {
        PositionCloseCommandEntity entity = new PositionCloseCommandEntity();
        entity.id = UUID.randomUUID();
        entity.accountId = UUID.randomUUID();
        entity.brokerAccountId = UUID.randomUUID();
        entity.brokerPositionReference = "ref";
        entity.resolvedMutationScope = "scope";
        entity.idempotencyKey = "key";
        entity.status = PositionCloseStatus.CREATED;
        entity.reconciliationResult = null;
        entity.externalOrderId = null;
        entity.failureReason = null;
        entity.createdAt = NOW;
        entity.updatedAt = NOW;
        entity.version = 0;

        PositionCloseCommand domain = PositionCloseCommandMapper.toDomain(entity);

        assertThat(domain.reconciliationResult).isNull();
        assertThat(domain.externalOrderId).isNull();
        assertThat(domain.failureReason).isNull();
    }

    @Test
    void toEntity_handlesNullOptionalFields() {
        PositionCloseCommand domain = new PositionCloseCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "ref", "scope", "key", PositionCloseStatus.CREATED,
                null, null, null, NOW, NOW, 0);

        PositionCloseCommandEntity entity = PositionCloseCommandMapper.toEntity(domain);

        assertThat(entity.reconciliationResult).isNull();
        assertThat(entity.externalOrderId).isNull();
        assertThat(entity.failureReason).isNull();
    }

    private PositionCloseCommandEntity buildEntity() {
        PositionCloseCommandEntity entity = new PositionCloseCommandEntity();
        entity.id = UUID.randomUUID();
        entity.accountId = UUID.randomUUID();
        entity.brokerAccountId = UUID.randomUUID();
        entity.brokerPositionReference = "BTC-USD-POS-42";
        entity.resolvedMutationScope = "a1b2c3d4-1234-5678-abcd-ef0123456789:spot:close";
        entity.idempotencyKey = "idemp-abc-123";
        entity.status = PositionCloseStatus.ACKNOWLEDGED;
        entity.reconciliationResult = ReconciliationCloseResult.EXPOSURE_CONFIRMED_ABSENT;
        entity.externalOrderId = "ext-order-999";
        entity.failureReason = null;
        entity.createdAt = NOW;
        entity.updatedAt = THEN;
        entity.version = 3L;
        return entity;
    }

    private PositionCloseCommand buildDomain() {
        return new PositionCloseCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "ETH-USD-POS-7", "b2c3d4e5-2345-6789-bcde-f01234567890:spot:close",
                "idemp-xyz-789", PositionCloseStatus.REJECTED,
                ReconciliationCloseResult.COMMAND_CONFIRMED_NOT_EXECUTED,
                null, "INSUFFICIENT_FUNDS", NOW, THEN, 7L);
    }
}
