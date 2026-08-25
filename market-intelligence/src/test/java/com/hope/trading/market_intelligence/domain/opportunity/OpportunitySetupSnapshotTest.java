package com.hope.trading.market_intelligence.domain.opportunity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 0029: the setup snapshot is the deterministic market context that
 * justified the opportunity at detection time. It must validate its content
 * and stay immutable.
 */
class OpportunitySetupSnapshotTest {

    private static final Instant DETECTED = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-25T09:59:00Z");

    private static OpportunitySetupSnapshot snapshot(
            BigDecimal referencePrice, Instant referencePriceAt) {
        return new OpportunitySetupSnapshot(
                referencePrice, referencePriceAt, "Price broke resistance with momentum",
                List.of(new OpportunityTrigger("directional_price_change", "12.5")),
                DETECTED);
    }

    @Test
    void validSnapshotExposesItsFacts() {
        var value = snapshot(new BigDecimal("64120.50"), OBSERVED_AT);

        assertThat(value.referencePrice()).isEqualByComparingTo("64120.50");
        assertThat(value.referencePriceAt()).isEqualTo(OBSERVED_AT);
        assertThat(value.description()).contains("broke resistance");
        assertThat(value.triggers()).hasSize(1);
        assertThat(value.detectedAt()).isEqualTo(DETECTED);
    }

    @Test
    void referencePriceAndTimestampMustBeProvidedTogether() {
        assertThatThrownBy(() -> snapshot(null, OBSERVED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> snapshot(new BigDecimal("100"), null))
                .isInstanceOf(IllegalArgumentException.class);
        // Both absent is legitimate (detection context without price data).
        assertThat(snapshot(null, null).referencePrice()).isNull();
    }

    @Test
    void blankDescriptionIsRejected() {
        assertThatThrownBy(() -> new OpportunitySetupSnapshot(
                null, null, "   ",
                List.of(new OpportunityTrigger("c", "1")), DETECTED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyTriggersAreRejectedAndListIsDefensivelyCopied() {
        assertThatThrownBy(() -> new OpportunitySetupSnapshot(
                null, null, "desc", List.of(), DETECTED))
                .isInstanceOf(IllegalArgumentException.class);

        List<OpportunityTrigger> mutable = new ArrayList<>();
        mutable.add(new OpportunityTrigger("condition", "7"));
        var value = new OpportunitySetupSnapshot(
                null, null, "desc", mutable, DETECTED);
        mutable.clear();
        assertThat(value.triggers()).hasSize(1);
    }

    @Test
    void detectedAtIsRequired() {
        assertThatThrownBy(() -> new OpportunitySetupSnapshot(
                null, null, "desc",
                List.of(new OpportunityTrigger("c", "1")), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankConditionIsRejectedButBlankObservedValueBecomesNull() {
        assertThatThrownBy(() -> new OpportunityTrigger("  ", "1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new OpportunityTrigger("c", "   ").observedValue()).isNull();
    }
}
