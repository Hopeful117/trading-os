package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChallengeInstrumentCompatibilityServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    @Test
    void acceptsMatchingProductType() {
        UUID accountId = UUID.randomUUID();
        ChallengeDefinition definition = definition(InstrumentProductType.SPOT);
        ChallengeInstance instance = ChallengeInstance.start(UUID.randomUUID(), accountId, definition,
                BigDecimal.TEN, NOW);
        ChallengeInstanceRepository instances = mock(ChallengeInstanceRepository.class);
        ChallengeDefinitionRepository definitions = mock(ChallengeDefinitionRepository.class);
        when(instances.findByAccountIdAndStatus(accountId, ChallengeStatus.ACTIVE)).thenReturn(java.util.Optional.of(instance));
        when(definitions.findById(new com.hope.trading.trading_core.challenge.domain.ChallengeDefinitionKey(
                definition.id(), definition.definitionVersion()))).thenReturn(java.util.Optional.of(definition));

        assertThatCode(() -> new ChallengeInstrumentCompatibilityService(instances, definitions)
                .validate(accountId, InstrumentProductType.SPOT)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMismatchedProductType() {
        UUID accountId = UUID.randomUUID();
        ChallengeDefinition definition = definition(InstrumentProductType.SPOT);
        ChallengeInstance instance = ChallengeInstance.start(UUID.randomUUID(), accountId, definition,
                BigDecimal.TEN, NOW);
        ChallengeInstanceRepository instances = mock(ChallengeInstanceRepository.class);
        ChallengeDefinitionRepository definitions = mock(ChallengeDefinitionRepository.class);
        when(instances.findByAccountIdAndStatus(accountId, ChallengeStatus.ACTIVE)).thenReturn(java.util.Optional.of(instance));
        when(definitions.findById(new com.hope.trading.trading_core.challenge.domain.ChallengeDefinitionKey(
                definition.id(), definition.definitionVersion()))).thenReturn(java.util.Optional.of(definition));

        assertThatThrownBy(() -> new ChallengeInstrumentCompatibilityService(instances, definitions)
                .validate(accountId, InstrumentProductType.LINEAR_PERPETUAL))
                .isInstanceOf(ChallengeInstrumentCompatibilityException.class)
                .extracting("code").isEqualTo("CHALLENGE_INSTRUMENT_PRODUCT_MISMATCH");
    }

    private static ChallengeDefinition definition(InstrumentProductType productType) {
        return ChallengeDefinition.create(UUID.randomUUID(), "1.0.0", "TEST_PROVIDER", "TEST_CHALLENGE",
                "PLAN_A", "USD", new BigDecimal("10000"), new BigDecimal("0.12"),
                ProgressionValueSource.BALANCE, SettlementModel.INVENTORY, productType,
                UUID.randomUUID(), "1.0.0", "https://example.test", NOW, NOW, "source", NOW);
    }
}
