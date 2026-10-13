package com.hope.trading.trading_core.challenge.api;

import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ChallengeDefinitionResponse(
        UUID id,
        String definitionVersion,
        String provider,
        String product,
        String planCode,
        String capitalCurrency,
        BigDecimal startingCapital,
        BigDecimal profitTargetRatio,
        ProgressionValueSource progressionValueSource,
        SettlementModel requiredSettlementModel,
        InstrumentProductType requiredInstrumentProductType,
        UUID riskPolicyId,
        String riskPolicyVersion,
        String sourceUrl,
        Instant retrievedAt,
        Instant effectiveFrom,
        String provenance
) {
    public static ChallengeDefinitionResponse from(ChallengeDefinition definition) {
        return new ChallengeDefinitionResponse(definition.id(), definition.definitionVersion(),
                definition.provider(), definition.product(), definition.planCode(),
                definition.capitalCurrency(), definition.startingCapital(), definition.profitTargetRatio(),
                definition.progressionValueSource(), definition.requiredSettlementModel(),
                definition.requiredInstrumentProductType(), definition.riskPolicyId(),
                definition.riskPolicyVersion(), definition.sourceUrl(), definition.retrievedAt(),
                definition.effectiveFrom(), definition.provenance());
    }
}
