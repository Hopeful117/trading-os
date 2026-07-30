package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.opportunity.*;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class TradingOpportunityPersistenceIntegrationTest {
    @Test
    void mapperRoundTripAndRepositoryPreserveEveryImmutableVersion() {
        InMemoryTradingOpportunityRepository repository =
                new InMemoryTradingOpportunityRepository();
        OpportunityId id = new OpportunityId(UUID.randomUUID());
        TradingOpportunity first = OpportunityTestFixtures.opportunity(
                id, 1, OpportunityStatus.DETECTED,
                new OpportunityScore(BigDecimal.valueOf(60)), OpportunityTestFixtures.NOW);
        TradingOpportunity second = OpportunityTestFixtures.opportunity(
                id, 2, OpportunityStatus.ANALYZED,
                new OpportunityScore(BigDecimal.valueOf(70)),
                OpportunityTestFixtures.NOW.plusSeconds(1));

        repository.append(first);
        repository.append(second);

        assertThat(repository.find(id, new OpportunityVersion(1)).orElseThrow())
                .usingRecursiveComparison().isEqualTo(first);
        assertThat(repository.findLatest(id).orElseThrow())
                .usingRecursiveComparison().isEqualTo(second);
        assertThat(repository.findHistory(id))
                .extracting(item -> item.version().value()).containsExactly(1L, 2L);
        assertThatThrownBy(() -> repository.append(second))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void userProjectionsAreIndependentForUsersSharingOneOpportunity() {
        InMemoryTradingOpportunityRepository opportunities =
                new InMemoryTradingOpportunityRepository();
        TradingOpportunity opportunity = OpportunityTestFixtures.opportunity(
                new OpportunityId(UUID.randomUUID()), 1, OpportunityStatus.ACTIVE,
                new OpportunityScore(BigDecimal.TEN), OpportunityTestFixtures.NOW);
        opportunities.append(opportunity);
        InMemoryUserOpportunityRepository projections =
                new InMemoryUserOpportunityRepository();
        UserOpportunityService service = new UserOpportunityService(
                projections, opportunities,
                Clock.fixed(OpportunityTestFixtures.NOW, ZoneOffset.UTC));
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        service.save(alice, opportunity.id(), true, false, true, true, 90, "Alice");
        service.save(bob, opportunity.id(), false, true, false, false, null, "Bob");

        assertThat(projections.find(alice, opportunity.id()).orElseThrow().favorite()).isTrue();
        assertThat(projections.find(bob, opportunity.id()).orElseThrow().hidden()).isTrue();
        assertThat(opportunity.getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("userId", "favorite", "hidden", "personalNotes");
    }
}
