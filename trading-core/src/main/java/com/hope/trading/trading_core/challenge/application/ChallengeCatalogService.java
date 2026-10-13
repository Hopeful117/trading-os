package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ChallengeCatalogService {
    private final ChallengeDefinitionRepository definitions;

    public ChallengeCatalogService(ChallengeDefinitionRepository definitions) {
        this.definitions = definitions;
    }

    @Transactional(readOnly = true)
    public List<ChallengeDefinition> selectable() {
        Map<UUID, ChallengeDefinition> current = new LinkedHashMap<>();
        definitions.findAllByOrderByIdAscEffectiveFromAscDefinitionVersionAsc()
                .forEach(definition -> current.merge(definition.id(), definition,
                        ChallengeCatalogService::latest));
        return new ArrayList<>(current.values());
    }

    private static ChallengeDefinition latest(ChallengeDefinition left, ChallengeDefinition right) {
        Comparator<ChallengeDefinition> order = Comparator
                .comparing(ChallengeDefinition::effectiveFrom)
                .thenComparing(ChallengeDefinition::definitionVersion);
        return order.compare(left, right) <= 0 ? right : left;
    }
}
