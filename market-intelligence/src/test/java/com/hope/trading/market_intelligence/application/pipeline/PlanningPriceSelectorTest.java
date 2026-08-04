package com.hope.trading.market_intelligence.application.pipeline;

import com.hope.trading.market_intelligence.domain.opportunity.OpportunityDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class PlanningPriceSelectorTest {
    private final PlanningPriceSelector selector = new PlanningPriceSelector();

    @Test
    void selectsAskForLongAndBidForShort() {
        assertThat(selector.select(
                OpportunityDirection.LONG, new BigDecimal("99"), new BigDecimal("101")))
                .isEqualTo(new PlanningPriceSelector.Selection("ASK", new BigDecimal("101")));
        assertThat(selector.select(
                OpportunityDirection.SHORT, new BigDecimal("99"), new BigDecimal("101")))
                .isEqualTo(new PlanningPriceSelector.Selection("BID", new BigDecimal("99")));
    }

    @Test
    void neverFallsBackToLastOrTheOppositeSide() {
        assertThatThrownBy(() -> selector.select(
                OpportunityDirection.LONG, new BigDecimal("99"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> selector.select(
                OpportunityDirection.NEUTRAL, new BigDecimal("99"), new BigDecimal("101")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
