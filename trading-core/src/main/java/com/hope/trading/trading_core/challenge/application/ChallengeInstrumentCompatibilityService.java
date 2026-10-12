package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ChallengeInstrumentCompatibilityService {
    private final ChallengeInstanceRepository instances;
    private final ChallengeDefinitionRepository definitions;

    public ChallengeInstrumentCompatibilityService(ChallengeInstanceRepository instances,
                                                   ChallengeDefinitionRepository definitions) {
        this.instances = instances;
        this.definitions = definitions;
    }

    public void validate(UUID accountId, InstrumentProductType actualProductType) {
        var active = instances.findByAccountIdAndStatus(accountId, ChallengeStatus.ACTIVE).orElse(null);
        if (active == null) return;

        ChallengeDefinition definition = definitions.findById(
                        new com.hope.trading.trading_core.challenge.domain.ChallengeDefinitionKey(
                                active.definitionId(), active.definitionVersion()))
                .orElseThrow(() -> new ChallengeInstrumentCompatibilityException(
                        "CHALLENGE_DEFINITION_UNAVAILABLE",
                        "The ACTIVE Challenge definition is unavailable"));
        if (definition.requiredInstrumentProductType() == null) return;
        if (actualProductType == null) {
            throw new ChallengeInstrumentCompatibilityException(
                    "INSTRUMENT_PRODUCT_IDENTITY_UNAVAILABLE",
                    "The instrument product identity is required by the ACTIVE Challenge");
        }
        if (definition.requiredInstrumentProductType() != actualProductType) {
            throw new ChallengeInstrumentCompatibilityException(
                    "CHALLENGE_INSTRUMENT_PRODUCT_MISMATCH",
                    "The instrument product type is incompatible with the ACTIVE Challenge");
        }
    }

    public boolean isRequired(UUID accountId) {
        var active = instances.findByAccountIdAndStatus(accountId, ChallengeStatus.ACTIVE).orElse(null);
        if (active == null) return false;
        return definitions.findById(new com.hope.trading.trading_core.challenge.domain.ChallengeDefinitionKey(
                        active.definitionId(), active.definitionVersion()))
                .map(definition -> definition.requiredInstrumentProductType() != null)
                .orElse(true);
    }
}
