package com.hope.trading.trading_core.execution.infrastructure.adapter;

import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.valueobject.BrokerOrderId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionAttemptId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import com.hope.trading.trading_core.execution.infrastructure.mapper.BrokerOrderMapper;
import com.hope.trading.trading_core.execution.infrastructure.persistence.BrokerFillEntity;
import com.hope.trading.trading_core.execution.infrastructure.persistence.BrokerOrderEntity;
import com.hope.trading.trading_core.execution.infrastructure.persistence.JpaBrokerFillRepository;
import com.hope.trading.trading_core.execution.infrastructure.persistence.JpaBrokerOrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JpaBrokerOrderAdapterTest {
    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    @Test
    void rejectsConflictingRewriteOfPersistedFill() {
        JpaBrokerOrderRepository orders = mock(JpaBrokerOrderRepository.class);
        JpaBrokerFillRepository fills = mock(JpaBrokerFillRepository.class);
        BrokerOrder order = order(new BigDecimal("100"));
        BrokerFillEntity existing = fill(order, new BigDecimal("101"));
        when(orders.findById(order.id().value())).thenReturn(Optional.of(orderEntity(order)));
        when(fills.findById("fill-1")).thenReturn(Optional.of(existing));

        JpaBrokerOrderAdapter adapter = new JpaBrokerOrderAdapter(orders, fills);

        assertThatThrownBy(() -> adapter.save(order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Broker fill is immutable: fill-1");
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void acceptsIdenticalReplayWithoutRewritingPersistedFill() {
        JpaBrokerOrderRepository orders = mock(JpaBrokerOrderRepository.class);
        JpaBrokerFillRepository fills = mock(JpaBrokerFillRepository.class);
        BrokerOrder order = order(new BigDecimal("100"));
        when(orders.findById(order.id().value())).thenReturn(Optional.of(orderEntity(order)));
        when(orders.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fills.findById("fill-1")).thenReturn(Optional.of(fill(order, new BigDecimal("100"))));
        when(fills.findAllByBrokerOrderIdOrderByExecutedAtAsc(order.id().value())).thenReturn(List.of());

        new JpaBrokerOrderAdapter(orders, fills).save(order);

        verify(fills, never()).saveAll(any());
    }

    private BrokerOrder order(BigDecimal price) {
        BrokerOrder order = BrokerOrder.acknowledged(BrokerOrderId.newId(), ExecutionIntentId.newId(),
                ExecutionAttemptId.newId(), "external-1", NOW);
        order.addFill(new BrokerOrder.Fill("fill-1", BigDecimal.ONE, price, BigDecimal.ZERO, NOW), true, NOW);
        return order;
    }

    private BrokerOrderEntity orderEntity(BrokerOrder order) {
        BrokerOrderEntity entity = new BrokerOrderEntity();
        entity.id = order.id().value();
        entity.intentId = order.intentId().value();
        entity.attemptId = order.attemptId().value();
        entity.externalOrderId = order.externalOrderId();
        entity.status = order.status().name();
        entity.createdAt = order.createdAt();
        entity.updatedAt = order.updatedAt();
        return entity;
    }

    private BrokerFillEntity fill(BrokerOrder order, BigDecimal price) {
        BrokerFillEntity entity = new BrokerFillEntity();
        entity.fillId = "fill-1";
        entity.brokerOrderId = order.id().value();
        entity.quantity = BigDecimal.ONE;
        entity.price = price;
        entity.fee = BigDecimal.ZERO;
        entity.executedAt = NOW;
        return entity;
    }
}
