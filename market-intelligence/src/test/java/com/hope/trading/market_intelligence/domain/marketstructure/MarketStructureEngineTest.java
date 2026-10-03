package com.hope.trading.market_intelligence.domain.marketstructure;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketStructureEngineTest {
    private final MarketStructureEngine engine = new MarketStructureEngine();

    @Test
    void confirmsOnlyAfterRadiusAndSuppressesWeakerSameTypePivots() {
        List<MarketStructureCandle> candles = candles(1, 2, 5, 3, 4, 2, 1);

        MarketStructureResult result = engine.extract(new MarketStructureInput(
                candles, List.of(), at(6), 1, 3));

        assertThat(result.retained()).extracting(MarketStructureSwing::type)
                .contains(MarketStructureSwingType.HIGH);
        assertThat(result.retained()).filteredOn(s -> s.type() == MarketStructureSwingType.HIGH)
                .singleElement().satisfies(s -> {
                    assertThat(s.pivotTime()).isEqualTo(at(3));
                    assertThat(s.confirmationTime()).isEqualTo(at(4));
                });
        assertThat(result.suppressed()).anySatisfy(s -> {
            assertThat(s.type()).isEqualTo(MarketStructureSwingType.HIGH);
            assertThat(s.suppressionReason()).isEqualTo("WITHIN_MINIMUM_SEPARATION");
        });
    }

    @Test
    void excludesPivotWhenConfirmationIsAfterCutoff() {
        MarketStructureResult result = engine.extract(new MarketStructureInput(
                candles(1, 2, 5, 3, 4, 2, 1), List.of(), at(2), 1, 3));

        assertThat(result.all()).isEmpty();
    }

    @Test
    void excludesCandidateWindowIntersectingGap() {
        MarketStructureResult result = engine.extract(new MarketStructureInput(
                candles(1, 2, 5, 3, 4, 2, 1),
                List.of(new MarketStructureGap(at(1), at(4))), at(6), 1, 3));

        assertThat(result.all()).isEmpty();
    }

    @Test
    void distinguishesValidEmptyEvidenceFromUnavailableEvidence() {
        MarketStructureResult empty = engine.extract(new MarketStructureInput(
                candles(1, 2, 3, 4, 5, 6, 7), List.of(), at(7), 1, 3));
        assertThat(empty.availability()).isEqualTo(MarketStructureAvailability.AVAILABLE);
        assertThat(empty.retained()).isEmpty();

        MarketStructureInput unavailable = new MarketStructureInput(
                new UUID(0, 2), "provider", "BTC/USD", "1m", candles(1, 2, 5), List.of(),
                at(3), "CONFIRMED_SWING_V1", "rules-v1", "policy", "1", "parameters",
                "input", MarketStructureEvidenceStatus.UNAVAILABLE, List.of("market-data unavailable"), 1, 2);
        MarketStructureResult unavailableResult = engine.extract(unavailable);
        assertThat(unavailableResult.availability()).isEqualTo(MarketStructureAvailability.UNAVAILABLE);
        assertThat(unavailableResult.retained()).isEmpty();
    }

    @Test
    void openOrSyntheticEvidenceCannotCreateOrConfirmAustwing() {
        List<MarketStructureCandle> open = new ArrayList<>(candles(1, 2, 5, 3, 4, 2, 1));
        open.set(3, new MarketStructureCandle(at(3), at(4), BigDecimal.valueOf(3), BigDecimal.ZERO,
                "c3", false, false));
        assertThat(engine.extract(new MarketStructureInput(open, List.of(), at(7), 1, 3)).all()).isEmpty();

        List<MarketStructureCandle> synthetic = new ArrayList<>(candles(1, 2, 5, 3, 4, 2, 1));
        synthetic.set(3, new MarketStructureCandle(at(3), at(4), BigDecimal.valueOf(3), BigDecimal.ZERO,
                "c3", true, true));
        assertThat(engine.extract(new MarketStructureInput(synthetic, List.of(), at(7), 1, 3)).all()).isEmpty();
    }

    @Test
    void t1DoesNotSeeAConfirmationThatIsAvailableAtT2() {
        List<MarketStructureCandle> values = candles(1, 2, 5, 3, 4, 2, 1);
        assertThat(engine.extract(new MarketStructureInput(values, List.of(), at(3), 1, 3)).all()).isEmpty();
        MarketStructureResult t2 = engine.extract(new MarketStructureInput(values, List.of(), at(4), 1, 3));
        assertThat(t2.retained()).isNotEmpty();
        assertThat(t2.retained()).allSatisfy(s -> assertThat(s.confirmationTime()).isBeforeOrEqualTo(at(4)));
    }

    @Test
    void preservesIdentityAndCompleteEvidenceWindowOnEveryPoint() {
        MarketStructureInput input = new MarketStructureInput(new UUID(0, 3), "kraken", "BTC/USD", "5m",
                candles(1, 2, 5, 3, 4, 2, 1), List.of(), at(7), "CONFIRMED_SWING_V1", "rules-v1",
                "policy", "2", "params-v2", "input-v2", MarketStructureEvidenceStatus.COMPLETE,
                List.of(), 1, 3);
        MarketStructureResult result = engine.extract(input);
        assertThat(result.marketId()).isEqualTo(input.marketId());
        assertThat(result.interval()).isEqualTo("5m");
        assertThat(result.parameterFingerprint()).isEqualTo("params-v2");
        assertThat(result.resultFingerprint()).isNotBlank();
        assertThat(result.retained()).allSatisfy(s -> {
            assertThat(s.evidenceSourceIds()).hasSize(3);
            assertThat(s.evidenceSources()).hasSize(3);
            assertThat(s.evidenceFrom()).isBefore(s.evidenceTo());
        });
    }

    @Test
    void rejectsConflictingDuplicateEvidenceWithoutUsingInputOrder() {
        List<MarketStructureCandle> values = new ArrayList<>(candles(1, 2, 5, 3, 4, 2, 1));
        values.add(new MarketStructureCandle(at(2), at(3), BigDecimal.valueOf(6), BigDecimal.ZERO,
                "conflict", true, false));

        MarketStructureResult result = engine.extract(new MarketStructureInput(values, List.of(), at(7), 1, 3));

        assertThat(result.availability()).isEqualTo(MarketStructureAvailability.INVALID);
        assertThat(result.findings()).anyMatch(value -> value.startsWith("DUPLICATE_CONFLICT"));
        assertThat(result.retained()).isEmpty();
    }

    private List<MarketStructureCandle> candles(int... highs) {
        return java.util.stream.IntStream.range(0, highs.length)
                .mapToObj(i -> new MarketStructureCandle(at(i), at(i + 1),
                        BigDecimal.valueOf(highs[i]), BigDecimal.ZERO, "c" + i, true, false))
                .toList();
    }

    private Instant at(int value) {
        return Instant.parse("2026-01-01T00:00:00Z").plusSeconds(value * 60L);
    }
}
