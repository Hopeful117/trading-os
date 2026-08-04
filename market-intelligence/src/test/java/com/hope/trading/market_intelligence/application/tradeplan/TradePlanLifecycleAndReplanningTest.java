package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.domain.tradeplan.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class TradePlanLifecycleAndReplanningTest {
    @Test
    void lifecycleAndIntegrationBoundariesValidateExactImmutableVersions() {
        var environment = TradePlanTestFixtures.environment();
        TradePlan proposed = ((TradePlanningResult.Success) environment.service().create(
                TradePlanTestFixtures.request(environment))).plan();
        var boundary = new DefaultTradePlanIntegrationBoundary(
                environment.plans(), environment.service());

        TradePlan accepted = environment.service().transition(
                proposed.id(), TradePlanStatus.ACCEPTED);
        assertThat(proposed.status()).isEqualTo(TradePlanStatus.PROPOSED);
        assertThat(boundary.loadAcceptedSnapshot(
                accepted.id(), accepted.version())).usingRecursiveComparison()
                .isEqualTo(accepted);
        TradePlan validated = boundary.recordRiskValidated(
                accepted.id(), accepted.version());
        TradePlan ready = boundary.markReadyToExecute(
                validated.id(), validated.version());
        assertThat(boundary.loadReadySnapshot(
                ready.id(), ready.version()).status())
                .isEqualTo(TradePlanStatus.READY_TO_EXECUTE);
        TradePlan executed = boundary.recordExecuted(ready.id(), ready.version());

        assertThat(executed.status()).isEqualTo(TradePlanStatus.EXECUTED);
        assertThat(environment.plans().history(proposed.id()))
                .extracting(item -> item.version().value())
                .containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThatThrownBy(() -> boundary.recordRiskValidated(
                accepted.id(), accepted.version()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidAndTerminalTransitionsAreRejected() {
        var policy = new TradePlanLifecyclePolicy();
        assertThatCode(() -> policy.validate(
                TradePlanStatus.DRAFT, TradePlanStatus.PROPOSED)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate(
                TradePlanStatus.PROPOSED, TradePlanStatus.ACCEPTED)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate(
                TradePlanStatus.PROPOSED, TradePlanStatus.EXECUTED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy.validate(
                TradePlanStatus.REJECTED, TradePlanStatus.ACCEPTED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void replanningUsesFreshContextAndPreservesOriginalSnapshotAndLineage() {
        var environment = TradePlanTestFixtures.environment();
        TradePlan first = ((TradePlanningResult.Success) environment.service().create(
                TradePlanTestFixtures.request(environment))).plan();
        TradePlanningContext fresh = TradePlanTestFixtures.context(
                environment.context().id(), 2, environment.owner());
        environment.contexts().saveSnapshot(fresh);
        var replanning = new TradePlanReplanningService(
                environment.plans(), environment.contexts(), environment.service());

        TradePlan second = ((TradePlanningResult.Success) replanning.replan(
                first.id(), environment.owner(), BigDecimal.valueOf(102),
                "market changed")).plan();

        assertThat(first.version().value()).isEqualTo(1);
        assertThat(first.planningContext().version()).isEqualTo(1);
        assertThat(second.version().value()).isEqualTo(2);
        assertThat(second.previousVersion()).contains(first.version());
        assertThat(second.planningContext().version()).isEqualTo(2);
        assertThat(environment.plans().findNext(
                first.id(), first.version()).orElseThrow().version()).isEqualTo(second.version());
        assertThat(environment.plans().history(first.id())).hasSize(2);
    }
}
