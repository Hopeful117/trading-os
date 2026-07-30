package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class OpportunityPoliciesTest {
    @Test
    void deduplicationUsesIdentityObservationSetAndInclusiveWindowBoundary() {
        TradingOpportunity existing = OpportunityTestFixtures.opportunity(
                new OpportunityId(UUID.randomUUID()), 1, OpportunityStatus.DETECTED,
                new OpportunityScore(BigDecimal.TEN), OpportunityTestFixtures.NOW);
        OpportunityIdentity same = new OpportunityIdentity(
                existing.instrument(), existing.direction(), existing.scenario(),
                existing.timeframe(), existing.observations());
        OpportunityDeduplicationPolicy policy =
                new OpportunityDeduplicationPolicy(Duration.ofMinutes(15));

        assertThat(policy.equivalent(
                same, existing, OpportunityTestFixtures.NOW.plus(Duration.ofMinutes(15)))).isTrue();
        assertThat(policy.equivalent(
                same, existing,
                OpportunityTestFixtures.NOW.plus(Duration.ofMinutes(15)).plusNanos(1))).isFalse();
        assertThat(policy.equivalent(new OpportunityIdentity(
                "ETH/EUR", same.direction(), same.scenario(), same.timeframe(),
                same.observations()), existing, OpportunityTestFixtures.NOW)).isFalse();
        assertThat(policy.equivalent(new OpportunityIdentity(
                same.instrument(), OpportunityDirection.SHORT, same.scenario(), same.timeframe(),
                same.observations()), existing, OpportunityTestFixtures.NOW)).isFalse();
        assertThat(policy.equivalent(new OpportunityIdentity(
                same.instrument(), same.direction(), "Other", same.timeframe(),
                same.observations()), existing, OpportunityTestFixtures.NOW)).isFalse();
        assertThat(policy.equivalent(new OpportunityIdentity(
                same.instrument(), same.direction(), same.scenario(), same.timeframe(),
                Set.of(new ObservationReference(UUID.randomUUID()))),
                existing, OpportunityTestFixtures.NOW)).isFalse();
    }

    @Test
    void expirationIsDeterministicAndRankingIsStableWithoutMutation() {
        OpportunityId firstId = new OpportunityId(
                UUID.fromString("11111111-1111-1111-1111-111111111111"));
        OpportunityId secondId = new OpportunityId(
                UUID.fromString("22222222-2222-2222-2222-222222222222"));
        TradingOpportunity first = OpportunityTestFixtures.opportunity(
                firstId, 1, OpportunityStatus.ACTIVE,
                new OpportunityScore(BigDecimal.valueOf(80)), OpportunityTestFixtures.NOW);
        TradingOpportunity second = OpportunityTestFixtures.opportunity(
                secondId, 1, OpportunityStatus.ACTIVE,
                new OpportunityScore(BigDecimal.valueOf(80)), OpportunityTestFixtures.NOW);

        assertThat(new ValidityWindowExpirationPolicy().evaluate(
                first, OpportunityTestFixtures.NOW.plusSeconds(300)).reason())
                .isEqualTo(ExpirationReason.VALIDITY_WINDOW_EXCEEDED);
        assertThat(new OpportunityRankingEngine().rank(
                List.of(second, first), OpportunityRankingEngine.highestScoreFirst(),
                ignored -> true)).extracting(TradingOpportunity::id)
                .containsExactly(firstId, secondId);
        assertThat(first.status()).isEqualTo(OpportunityStatus.ACTIVE);
    }

    @Test
    void lifecycleAllowsOnlyAdrTransitions() {
        OpportunityLifecyclePolicy policy = new OpportunityLifecyclePolicy();
        assertThatCode(() -> policy.validate(
                OpportunityStatus.DETECTED, OpportunityStatus.ANALYZED)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate(
                OpportunityStatus.DETECTED, OpportunityStatus.ACTIVE))
                .isInstanceOf(IllegalOpportunityTransitionException.class);
        assertThatThrownBy(() -> policy.validate(
                OpportunityStatus.EXPIRED, OpportunityStatus.ACTIVE))
                .isInstanceOf(IllegalOpportunityTransitionException.class);
    }
}
