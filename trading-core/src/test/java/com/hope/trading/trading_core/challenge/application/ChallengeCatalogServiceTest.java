package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChallengeCatalogServiceTest {
    private static final Instant FIRST = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-02-01T00:00:00Z");

    @Test
    void returnsOnlyTheLatestVersionForEachLogicalDefinition() {
        ChallengeDefinitionRepository repository = mock(ChallengeDefinitionRepository.class);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(repository.findAllByOrderByIdAscEffectiveFromAscDefinitionVersionAsc()).thenReturn(List.of(
                definition(firstId, "1.0.0", FIRST),
                definition(firstId, "2.0.0", SECOND),
                definition(secondId, "1.0.0", FIRST)));

        var result = new ChallengeCatalogService(repository).selectable();

        assertThat(result).extracting(ChallengeDefinition::id)
                .containsExactly(firstId, secondId);
        assertThat(result.get(0).definitionVersion()).isEqualTo("2.0.0");
    }

    @Test
    void usesVersionAsDeterministicTieBreaker() {
        ChallengeDefinitionRepository repository = mock(ChallengeDefinitionRepository.class);
        UUID definitionId = UUID.randomUUID();
        when(repository.findAllByOrderByIdAscEffectiveFromAscDefinitionVersionAsc()).thenReturn(List.of(
                definition(definitionId, "1.0.0", FIRST),
                definition(definitionId, "1.1.0", FIRST)));

        var result = new ChallengeCatalogService(repository).selectable();

        assertThat(result).singleElement().extracting(ChallengeDefinition::definitionVersion)
                .isEqualTo("1.1.0");
    }

    private static ChallengeDefinition definition(UUID id, String version, Instant effectiveFrom) {
        return ChallengeDefinition.create(id, version, "PROVIDER", "PRODUCT", "PLAN", "USD",
                new BigDecimal("10000"), new BigDecimal("0.1"), ProgressionValueSource.BALANCE,
                SettlementModel.CASH, UUID.randomUUID(), "1.0.0", "https://example.test", effectiveFrom,
                effectiveFrom, "test", effectiveFrom);
    }
}
