package com.hope.trading.broker_service.ctrader.experimentation;

import com.hope.trading.broker_service.ctrader.synchronization.CTraderReadOnlyObservation;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Offline-only experiment for a non-authoritative cTrader equity candidate. */
public final class CTraderDerivedEquityExperiment {
    public static final String CALCULATION_VERSION = "CTRADER_DERIVED_EQUITY_V1";

    private CTraderDerivedEquityExperiment() {
    }

    public static DerivedEquityExperimentResult evaluate(CTraderReadOnlyObservation observation,
                                                          Instant now,
                                                          Duration maximumAge,
                                                          Duration maximumCollectionWindow) {
        Objects.requireNonNull(observation, "observation is required");
        return evaluate(new Input(observation.externalAccountId(), observation.valuationAsset(),
                        observation.balance(), observation), now, maximumAge, maximumCollectionWindow);
    }

    public static DerivedEquityExperimentResult evaluate(Input input, Instant now,
                                                          Duration maximumAge,
                                                          Duration maximumCollectionWindow) {
        Objects.requireNonNull(input, "input is required");
        Objects.requireNonNull(now, "now is required");
        Objects.requireNonNull(maximumAge, "maximumAge is required");
        Objects.requireNonNull(maximumCollectionWindow, "maximumCollectionWindow is required");

        CTraderReadOnlyObservation observation = input.observation();
        List<String> diagnostics = new java.util.ArrayList<>();
        if (input.externalAccountId() == null || input.externalAccountId().isBlank()) {
            diagnostics.add("EXTERNAL_ACCOUNT_ID_UNAVAILABLE");
        }
        if (input.valuationAsset() == null || input.valuationAsset().isBlank()) {
            diagnostics.add("VALUATION_ASSET_UNAVAILABLE");
        }
        if (input.balance() == null) {
            diagnostics.add("BALANCE_UNAVAILABLE");
        }
        if (!diagnostics.isEmpty()) {
            return unavailable(input, ConsistencyStatus.INCOMPLETE, diagnostics);
        }
        ConsistencyStatus temporalStatus = temporalStatus(observation, now, maximumAge,
                maximumCollectionWindow, diagnostics);
        if (temporalStatus != ConsistencyStatus.CONSISTENT_WITH_OBSERVATIONS) {
            return unavailable(input, temporalStatus, diagnostics);
        }
        if (!observation.complete() || !observation.pnlComplete()
                || !available(observation.positionSet()) || !available(observation.pnlSet())) {
            diagnostics.add("POSITION_AND_PNL_COLLECTIONS_MUST_BE_COMPLETE");
            return unavailable(input, ConsistencyStatus.INCOMPLETE, diagnostics);
        }

        Map<String, CTraderReadOnlyObservation.Position> positions = uniquePositions(observation, diagnostics);
        Map<String, CTraderReadOnlyObservation.PositionPnl> pnls = uniquePnls(observation, diagnostics);
        if (positions == null || pnls == null || !positions.keySet().equals(pnls.keySet())) {
            diagnostics.add("POSITION_AND_PNL_IDENTITIES_DO_NOT_MATCH");
            return unavailable(input, ConsistencyStatus.INCOHERENT, diagnostics);
        }

        BigDecimal totalNetUnrealizedPnl = BigDecimal.ZERO;
        for (CTraderReadOnlyObservation.PositionPnl pnl : pnls.values()) {
            if (!pnl.netUnrealizedPnl().present()) {
                diagnostics.add("NET_UNREALIZED_PNL_UNAVAILABLE:" + pnl.providerPositionReference());
                return unavailable(input, ConsistencyStatus.INCOMPLETE, diagnostics);
            }
            totalNetUnrealizedPnl = totalNetUnrealizedPnl.add(pnl.netUnrealizedPnl().value());
        }

        diagnostics.add("SWAP_COMMISSION_AND_MIRRORING_COMMISSION_NOT_INCLUDED_UNVERIFIED");
        return new DerivedEquityExperimentResult(
                input.externalAccountId(), input.valuationAsset(), input.balance(),
                totalNetUnrealizedPnl, input.balance().add(totalNetUnrealizedPnl),
                List.copyOf(positions.keySet()), observation.startedAt(), observation.providerObservedAt(),
                observation.completedAt(), CALCULATION_VERSION,
                DerivedEquityHypothesis.BALANCE_PLUS_NET_UNREALIZED_PNL_V1,
                ConsistencyStatus.CONSISTENT_WITH_OBSERVATIONS,
                DerivedEquityValidationStatus.UNVERIFIED, List.copyOf(diagnostics));
    }

    public static ConsistencyCheck compare(CTraderReadOnlyObservation first,
                                            CTraderReadOnlyObservation second) {
        Objects.requireNonNull(first, "first observation is required");
        Objects.requireNonNull(second, "second observation is required");
        List<String> diagnostics = new java.util.ArrayList<>();
        ConsistencyStatus firstStatus = comparableStatus(first, diagnostics, "FIRST");
        ConsistencyStatus secondStatus = comparableStatus(second, diagnostics, "SECOND");
        if (firstStatus != ConsistencyStatus.CONSISTENT_WITH_OBSERVATIONS
                || secondStatus != ConsistencyStatus.CONSISTENT_WITH_OBSERVATIONS) {
            return new ConsistencyCheck(firstStatus != ConsistencyStatus.CONSISTENT_WITH_OBSERVATIONS
                    ? firstStatus : secondStatus, diagnostics);
        }
        if (!first.externalAccountId().equals(second.externalAccountId())
                || !first.valuationAsset().equals(second.valuationAsset())) {
            diagnostics.add("ACCOUNT_IDENTITY_OR_VALUATION_ASSET_CHANGED");
            return new ConsistencyCheck(ConsistencyStatus.INCOHERENT, diagnostics);
        }
        if (first.accountFacts().balanceVersion().present()
                && second.accountFacts().balanceVersion().present()
                && !first.accountFacts().balanceVersion().value()
                .equals(second.accountFacts().balanceVersion().value())) {
            diagnostics.add("BALANCE_VERSION_CHANGED");
            return new ConsistencyCheck(ConsistencyStatus.INCOHERENT, diagnostics);
        }
        if (!sameAmount(first.balance(), second.balance())
                || !samePositions(first.positions(), second.positions())
                || !samePnls(first.pnlSet().values(), second.pnlSet().values())) {
            diagnostics.add("OBSERVED_ACCOUNT_POSITION_OR_PNL_FACT_CHANGED");
            return new ConsistencyCheck(ConsistencyStatus.INCOHERENT, diagnostics);
        }
        if (!first.complete() || !second.complete() || !first.pnlComplete() || !second.pnlComplete()) {
            diagnostics.add("ONE_OR_MORE_OBSERVATIONS_INCOMPLETE");
            return new ConsistencyCheck(ConsistencyStatus.INCOMPLETE, diagnostics);
        }
        if (!first.accountFacts().balanceVersion().present()
                || !second.accountFacts().balanceVersion().present()) {
            diagnostics.add("BALANCE_VERSION_UNAVAILABLE");
            return new ConsistencyCheck(ConsistencyStatus.UNVERIFIED, diagnostics);
        }
        return new ConsistencyCheck(ConsistencyStatus.CONSISTENT_WITH_OBSERVATIONS, List.of());
    }

    private static DerivedEquityExperimentResult unavailable(Input input,
                                                              ConsistencyStatus status,
                                                              List<String> diagnostics) {
        CTraderReadOnlyObservation observation = input.observation();
        return new DerivedEquityExperimentResult(input.externalAccountId(), input.valuationAsset(), input.balance(),
                null, null, List.of(), observation.startedAt(), observation.providerObservedAt(), observation.completedAt(),
                CALCULATION_VERSION,
                DerivedEquityHypothesis.BALANCE_PLUS_NET_UNREALIZED_PNL_V1,
                status, DerivedEquityValidationStatus.UNVERIFIED, diagnostics);
    }

    private static ConsistencyStatus temporalStatus(CTraderReadOnlyObservation observation, Instant now,
                                                    Duration maximumAge, Duration maximumCollectionWindow,
                                                    List<String> diagnostics) {
        if (observation.startedAt().isAfter(observation.completedAt())
                || observation.providerObservedAt().isBefore(observation.startedAt())
                || observation.providerObservedAt().isAfter(observation.completedAt())) {
            diagnostics.add("OBSERVATION_TIMESTAMP_ORDER_INVALID");
            return ConsistencyStatus.UNVERIFIED;
        }
        if (observation.providerObservedAt().isAfter(now)
                || Duration.between(observation.providerObservedAt(), now).compareTo(maximumAge) > 0) {
            diagnostics.add("OBSERVATION_STALE");
            return ConsistencyStatus.STALE;
        }
        if (Duration.between(observation.startedAt(), observation.completedAt())
                .compareTo(maximumCollectionWindow) > 0) {
            diagnostics.add("COLLECTION_WINDOW_EXCEEDED");
            return ConsistencyStatus.UNVERIFIED;
        }
        return ConsistencyStatus.CONSISTENT_WITH_OBSERVATIONS;
    }

    private static ConsistencyStatus comparableStatus(CTraderReadOnlyObservation observation,
                                                       List<String> diagnostics, String label) {
        if (observation.startedAt().isAfter(observation.completedAt())
                || observation.providerObservedAt().isBefore(observation.startedAt())
                || observation.providerObservedAt().isAfter(observation.completedAt())) {
            diagnostics.add(label + "_OBSERVATION_TIMESTAMP_ORDER_INVALID");
            return ConsistencyStatus.UNVERIFIED;
        }
        if (!observation.complete() || !observation.pnlComplete()
                || !available(observation.positionSet()) || !available(observation.pnlSet())) {
            diagnostics.add(label + "_COLLECTIONS_INCOMPLETE");
            return ConsistencyStatus.INCOMPLETE;
        }
        Map<String, CTraderReadOnlyObservation.Position> positions = uniquePositions(observation, diagnostics);
        Map<String, CTraderReadOnlyObservation.PositionPnl> pnls = uniquePnls(observation, diagnostics);
        if (positions == null || pnls == null || !positions.keySet().equals(pnls.keySet())) {
            diagnostics.add(label + "_POSITION_AND_PNL_IDENTITIES_DO_NOT_MATCH");
            return ConsistencyStatus.INCOHERENT;
        }
        if (!positions.keySet().equals(positionIds(observation.positions()))) {
            diagnostics.add(label + "_POSITION_COLLECTIONS_DO_NOT_MATCH");
            return ConsistencyStatus.INCOHERENT;
        }
        if (pnls.values().stream().anyMatch(pnl -> !pnl.netUnrealizedPnl().present())) {
            diagnostics.add(label + "_NET_UNREALIZED_PNL_UNAVAILABLE");
            return ConsistencyStatus.INCOMPLETE;
        }
        return ConsistencyStatus.CONSISTENT_WITH_OBSERVATIONS;
    }

    private static boolean available(CTraderReadOnlyObservation.CollectionState<?> state) {
        return state.availability() == CTraderReadOnlyObservation.CollectionAvailability.AVAILABLE_EMPTY
                || state.availability() == CTraderReadOnlyObservation.CollectionAvailability.AVAILABLE_NON_EMPTY;
    }

    private static Map<String, CTraderReadOnlyObservation.Position> uniquePositions(
            CTraderReadOnlyObservation observation, List<String> diagnostics) {
        Map<String, CTraderReadOnlyObservation.Position> result = new HashMap<>();
        for (CTraderReadOnlyObservation.Position position : observation.positionSet().values()) {
            if (result.put(position.providerReference(), position) != null) {
                diagnostics.add("DUPLICATE_POSITION_ID:" + position.providerReference());
                return null;
            }
        }
        return result;
    }

    private static Map<String, CTraderReadOnlyObservation.PositionPnl> uniquePnls(
            CTraderReadOnlyObservation observation, List<String> diagnostics) {
        Map<String, CTraderReadOnlyObservation.PositionPnl> result = new HashMap<>();
        for (CTraderReadOnlyObservation.PositionPnl pnl : observation.pnlSet().values()) {
            if (result.put(pnl.providerPositionReference(), pnl) != null) {
                diagnostics.add("DUPLICATE_PNL_ID:" + pnl.providerPositionReference());
                return null;
            }
        }
        return result;
    }

    private static boolean sameAmount(BigDecimal first, BigDecimal second) {
        return first.compareTo(second) == 0;
    }

    private static boolean samePositions(List<CTraderReadOnlyObservation.Position> first,
                                         List<CTraderReadOnlyObservation.Position> second) {
        Map<String, CTraderReadOnlyObservation.Position> firstById = positionsById(first);
        Map<String, CTraderReadOnlyObservation.Position> secondById = positionsById(second);
        if (!firstById.keySet().equals(secondById.keySet())) return false;
        return firstById.keySet().stream().allMatch(id -> {
            var firstPosition = firstById.get(id);
            var secondPosition = secondById.get(id);
            return firstPosition.instrument().equals(secondPosition.instrument())
                    && sameAmount(firstPosition.signedQuantity(), secondPosition.signedQuantity())
                    && sameAmount(firstPosition.entryPrice(), secondPosition.entryPrice())
                    && firstPosition.observedAt().equals(secondPosition.observedAt());
        });
    }

    private static Set<String> positionIds(List<CTraderReadOnlyObservation.Position> positions) {
        Set<String> result = new java.util.HashSet<>();
        positions.forEach(position -> result.add(position.providerReference()));
        return result;
    }

    private static Map<String, CTraderReadOnlyObservation.Position> positionsById(
            List<CTraderReadOnlyObservation.Position> positions) {
        Map<String, CTraderReadOnlyObservation.Position> result = new HashMap<>();
        positions.forEach(position -> result.put(position.providerReference(), position));
        return result;
    }

    private static boolean samePnls(List<CTraderReadOnlyObservation.PositionPnl> first,
                                    List<CTraderReadOnlyObservation.PositionPnl> second) {
        Map<String, BigDecimal> firstValues = pnlValues(first);
        Map<String, BigDecimal> secondValues = pnlValues(second);
        return firstValues.keySet().equals(secondValues.keySet())
                && firstValues.keySet().stream()
                .allMatch(id -> sameAmount(firstValues.get(id), secondValues.get(id)));
    }

    private static Map<String, BigDecimal> pnlValues(List<CTraderReadOnlyObservation.PositionPnl> pnls) {
        Map<String, BigDecimal> values = new HashMap<>();
        pnls.forEach(pnl -> {
            if (pnl.netUnrealizedPnl().present()) {
                values.put(pnl.providerPositionReference(), pnl.netUnrealizedPnl().value());
            }
        });
        return values;
    }

    public enum DerivedEquityHypothesis {
        BALANCE_PLUS_NET_UNREALIZED_PNL_V1
    }

    public enum DerivedEquityValidationStatus {
        UNVERIFIED
    }

    public enum ConsistencyStatus {
        CONSISTENT_WITH_OBSERVATIONS,
        INCOMPLETE,
        INCOHERENT,
        STALE,
        UNVERIFIED
    }

    public record Input(String externalAccountId, String valuationAsset, BigDecimal balance,
                        CTraderReadOnlyObservation observation) {
        public Input {
            Objects.requireNonNull(observation, "observation is required");
        }
    }

    public record ConsistencyCheck(ConsistencyStatus status, List<String> diagnostics) {
        public ConsistencyCheck {
            status = Objects.requireNonNull(status, "status is required");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public record DerivedEquityExperimentResult(
            String externalAccountId,
            String valuationAsset,
            BigDecimal balance,
            BigDecimal totalNetUnrealizedPnl,
            BigDecimal candidateEquity,
            List<String> positionReferences,
            Instant startedAt,
            Instant providerObservedAt,
            Instant completedAt,
            String calculationVersion,
            DerivedEquityHypothesis hypothesis,
            ConsistencyStatus consistencyStatus,
            DerivedEquityValidationStatus validationStatus,
            List<String> diagnostics) {
        public DerivedEquityExperimentResult {
            positionReferences = List.copyOf(positionReferences);
            Objects.requireNonNull(startedAt, "startedAt is required");
            Objects.requireNonNull(providerObservedAt, "providerObservedAt is required");
            Objects.requireNonNull(completedAt, "completedAt is required");
            Objects.requireNonNull(calculationVersion, "calculationVersion is required");
            Objects.requireNonNull(hypothesis, "hypothesis is required");
            Objects.requireNonNull(consistencyStatus, "consistencyStatus is required");
            Objects.requireNonNull(validationStatus, "validationStatus is required");
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
