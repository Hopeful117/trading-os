package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.HistoricalEvidenceRetirementBusinessState;
import com.hope.trading.market_data.model.HistoricalEvidenceRetirementOperationalState;
import com.hope.trading.market_data.model.HistoricalEvidenceRetirementScope;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class OrderBookSourceRetirementServiceTest {
    private static final UUID OPERATION_ID = UUID.randomUUID();
    private static final HistoricalEvidenceRetirementScope SCOPE = new HistoricalEvidenceRetirementScope(
            MarketProvider.KRAKEN, "PF_XBTUSD", UUID.randomUUID(), 25);

    @Test
    void requiresPreparedInventoryFingerprint() {
        OrderBookSourceRetirementRepository repository = mock(OrderBookSourceRetirementRepository.class);
        OrderBookSourceRetirementService service = service(repository);
        when(repository.findForUpdate(OPERATION_ID)).thenReturn(Optional.of(operation()), Optional.of(operation(
                HistoricalEvidenceRetirementBusinessState.SOURCE_RETIRED, "inventory")));

        assertThatThrownBy(() -> service.sourceRetired(OPERATION_ID, "stale", "market-intelligence", "ack"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match prepared inventory");
    }

    @Test
    void usesTheAuthenticatedServiceActorForTheTransition() {
        OrderBookSourceRetirementRepository repository = mock(OrderBookSourceRetirementRepository.class);
        OrderBookSourceRetirementService service = service(repository);
        when(repository.findForUpdate(OPERATION_ID)).thenReturn(Optional.of(operation()));
        when(repository.preflight(SCOPE)).thenReturn(preflight(true));
        when(repository.transitionSourceRetired(eq(OPERATION_ID), eq("inventory"), eq("market-intelligence"),
                eq("ack"), any(), any(), any())).thenReturn(true);
        when(repository.find(OPERATION_ID)).thenReturn(Optional.of(operation(
                HistoricalEvidenceRetirementBusinessState.SOURCE_RETIRED, "inventory")));

        service.sourceRetired(OPERATION_ID, "inventory", "market-intelligence", "ack");

        verify(repository).transitionSourceRetired(eq(OPERATION_ID), eq("inventory"), eq("market-intelligence"),
                eq("ack"), any(), any(), any());
    }

    @Test
    void preparationAllowsKnownEligibleDependencies() {
        OrderBookSourceRetirementRepository repository = mock(OrderBookSourceRetirementRepository.class);
        OrderBookSourceRetirementService service = service(repository);
        when(repository.approvalValid(SCOPE, "approval", "operator", Instant.parse("2026-11-09T00:00:00Z")))
                .thenReturn(true);
        when(repository.preflight(SCOPE)).thenReturn(preflight(true));
        when(repository.savePrepared(any())).thenReturn(true);

        assertThat(service.prepare(new OrderBookSourceRetirementService.PrepareRequest(SCOPE, "approval", "operator")))
                .extracting(OrderBookSourceRetirementService.OperationView::businessState)
                .isEqualTo(HistoricalEvidenceRetirementBusinessState.PREPARED);
    }

    @ParameterizedTest
    @MethodSource("blockingPreflights")
    void preparationRejectsIneligibleDependencyInventory(OrderBookSourceRetirementRepository.Preflight preflight) {
        OrderBookSourceRetirementRepository repository = mock(OrderBookSourceRetirementRepository.class);
        OrderBookSourceRetirementService service = service(repository);
        when(repository.approvalValid(SCOPE, "approval", "operator", Instant.parse("2026-11-09T00:00:00Z")))
                .thenReturn(true);
        when(repository.preflight(SCOPE)).thenReturn(preflight);

        assertThatThrownBy(() -> service.prepare(
                new OrderBookSourceRetirementService.PrepareRequest(SCOPE, "approval", "operator")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source-retirement preflight failed");
        verify(repository, never()).savePrepared(any());
    }

    @Test
    void revalidatesInventoryBeforeSourceRetirement() {
        OrderBookSourceRetirementRepository repository = mock(OrderBookSourceRetirementRepository.class);
        OrderBookSourceRetirementService service = service(repository);
        when(repository.findForUpdate(OPERATION_ID)).thenReturn(Optional.of(operation()));
        when(repository.preflight(SCOPE)).thenReturn(preflight(false));

        assertThatThrownBy(() -> service.sourceRetired(OPERATION_ID, "inventory", "market-intelligence", "ack"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inventory is stale");
        verify(repository, never()).transitionSourceRetired(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void physicalOperationalStatesRequireSourceRetiredBusinessState() {
        OrderBookSourceRetirementRepository repository = mock(OrderBookSourceRetirementRepository.class);
        OrderBookSourceRetirementService service = service(repository);
        when(repository.findForUpdate(OPERATION_ID)).thenReturn(Optional.of(operation()));

        assertThatThrownBy(() -> service.transitionOperational(OPERATION_ID,
                HistoricalEvidenceRetirementOperationalState.PHYSICAL_IN_PROGRESS,
                "market-intelligence", "physical operation", "change-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SOURCE_RETIRED");
        verify(repository, never()).transitionOperational(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void physicalOperationalStatesAreAllowedAfterSourceRetirement() {
        OrderBookSourceRetirementRepository repository = mock(OrderBookSourceRetirementRepository.class);
        OrderBookSourceRetirementService service = service(repository);
        when(repository.findForUpdate(OPERATION_ID)).thenReturn(Optional.of(operation(
                HistoricalEvidenceRetirementBusinessState.SOURCE_RETIRED, "inventory",
                HistoricalEvidenceRetirementOperationalState.QUIESCENCE_CONFIRMED)));
        when(repository.transitionOperational(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);
        when(repository.find(OPERATION_ID)).thenReturn(Optional.of(operation(
                HistoricalEvidenceRetirementBusinessState.SOURCE_RETIRED, "inventory",
                HistoricalEvidenceRetirementOperationalState.PHYSICAL_IN_PROGRESS)));

        assertThat(service.transitionOperational(OPERATION_ID,
                HistoricalEvidenceRetirementOperationalState.PHYSICAL_IN_PROGRESS,
                "market-intelligence", "physical operation", "change-123").businessState())
                .isEqualTo(HistoricalEvidenceRetirementBusinessState.SOURCE_RETIRED);
    }

    private static Stream<OrderBookSourceRetirementRepository.Preflight> blockingPreflights() {
        return Stream.of(
                preflightWith(1, 0, 1, 0, 0),
                preflightWith(1, 0, 0, 1, 0),
                preflightWith(1, 0, 0, 0, 1));
    }

    private OrderBookSourceRetirementService service(OrderBookSourceRetirementRepository repository) {
        return new OrderBookSourceRetirementService(repository,
                Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC));
    }

    private OrderBookSourceRetirementRepository.Operation operation() {
        return operation(HistoricalEvidenceRetirementBusinessState.PREPARED, null);
    }

    private OrderBookSourceRetirementRepository.Operation operation(
            HistoricalEvidenceRetirementBusinessState businessState, String dependencyFingerprint) {
        return operation(businessState, dependencyFingerprint,
                HistoricalEvidenceRetirementOperationalState.PREFLIGHT_PASSED);
    }

    private OrderBookSourceRetirementRepository.Operation operation(
            HistoricalEvidenceRetirementBusinessState businessState, String dependencyFingerprint,
            HistoricalEvidenceRetirementOperationalState operationalState) {
        Instant now = Instant.parse("2026-11-09T00:00:00Z");
        return new OrderBookSourceRetirementRepository.Operation(OPERATION_ID, "operation-fingerprint", SCOPE,
                "approval", "operator", businessState, operationalState,
                "preflight", "inventory", dependencyFingerprint == null ? "inventory" : dependencyFingerprint,
                null, now, now);
    }

    private static OrderBookSourceRetirementRepository.Preflight preflight(boolean passed) {
        return passed ? preflightWith(51, 51, 0, 0, 0)
                : preflightWith(51, 50, 0, 0, 0);
    }

    private static OrderBookSourceRetirementRepository.Preflight preflightWith(
            long dependencies, long eligible, long unknown, long contradictory, long holds) {
        return new OrderBookSourceRetirementRepository.Preflight(SCOPE, 100, 100, 0, 51,
                dependencies, eligible, unknown, contradictory, holds, 0, true,
                "preflight", "inventory");
    }
}
