package com.hope.trading.market_intelligence.strategy.adapter.persistence;

import com.hope.trading.market_intelligence.strategy.application.StrategyDefinitionRepository;
import com.hope.trading.market_intelligence.strategy.domain.RequiredSemanticInput;
import com.hope.trading.market_intelligence.strategy.domain.SemanticInputType;
import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;
import com.hope.trading.market_intelligence.strategy.domain.StrategyOperationalStatus;
import com.hope.trading.market_intelligence.strategy.domain.StrategyParameter;
import com.hope.trading.market_intelligence.strategy.domain.StrategyParameters;
import com.hope.trading.market_intelligence.strategy.domain.ValidationStatus;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Base64;

@Repository
public class JpaStrategyDefinitionRepository implements StrategyDefinitionRepository {

    private static final String SEPARATOR = ",";
    private final SpringDataStrategyDefinitionRepository jpa;

    public JpaStrategyDefinitionRepository(SpringDataStrategyDefinitionRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public StrategyDefinition save(StrategyDefinition definition) {
        JpaStrategyDefinitionEntity.Pk key = new JpaStrategyDefinitionEntity.Pk(
                definition.strategyId().value(), definition.version());
        JpaStrategyDefinitionEntity entity = jpa.findById(key).map(existing -> {
            StrategyDefinition persisted = toDomain(existing);
            if (!sameSemantics(persisted, definition)) {
                throw new IllegalArgumentException(
                        "strategy semantics are immutable for " + definition.strategyId()
                                + " version " + definition.version());
            }
            return existing;
        }).orElseGet(JpaStrategyDefinitionEntity::new);
        entity.setStrategyId(definition.strategyId().value());
        entity.setVersion(definition.version());
        entity.setName(definition.name());
        entity.setDescription(definition.description());
        entity.setScenario(definition.scenario());
        entity.setOperationalStatus(definition.operationalStatus().name());
        entity.setValidationStatus(definition.validationStatus().name());
        entity.setDirection(definition.direction().name());
        entity.setAssetClasses(join(definition.applicability().assetClasses()));
        entity.setTimeframes(join(definition.applicability().timeframes().stream()
                .map(Enum::name).collect(Collectors.toSet())));
        entity.setProviders(join(definition.applicability().providers()));
        entity.setRequiredInputs(join(definition.requiredInputs().stream()
                .map(RequiredSemanticInput::toString).collect(Collectors.toCollection(LinkedHashSet::new))));
        entity.setParameters(serializeParameters(definition.parameters()));
        entity.setResearchRef(definition.researchRef());
        entity.setValidationEvidenceRef(definition.validationEvidenceRef());
        entity.setCreatedAt(definition.createdAt());
        entity.setUpdatedAt(definition.updatedAt());
        return toDomain(jpa.save(entity));
    }

    private static boolean sameSemantics(StrategyDefinition left, StrategyDefinition right) {
        return left.strategyId().equals(right.strategyId())
                && left.version() == right.version()
                && Objects.equals(left.name(), right.name())
                && Objects.equals(left.description(), right.description())
                && Objects.equals(left.scenario(), right.scenario())
                && left.direction() == right.direction()
                && Objects.equals(left.applicability(), right.applicability())
                && Objects.equals(left.requiredInputs(), right.requiredInputs())
                && Objects.equals(left.parameters(), right.parameters())
                && Objects.equals(left.researchRef(), right.researchRef())
                && Objects.equals(left.createdAt(), right.createdAt());
    }

    @Override
    public java.util.Optional<StrategyDefinition> find(StrategyId strategyId, int version) {
        return jpa.findById(new JpaStrategyDefinitionEntity.Pk(strategyId.value(), version))
                .map(JpaStrategyDefinitionRepository::toDomain);
    }

    @Override
    public List<StrategyDefinition> findAllVersions(StrategyId strategyId) {
        return jpa.findByStrategyIdOrderByVersionAsc(strategyId.value()).stream()
                .map(JpaStrategyDefinitionRepository::toDomain)
                .toList();
    }

    @Override
    public List<StrategyDefinition> findAll() {
        return jpa.findAll().stream()
                .map(JpaStrategyDefinitionRepository::toDomain)
                .toList();
    }

    private static StrategyDefinition toDomain(JpaStrategyDefinitionEntity entity) {
        return StrategyDefinition.rehydrate(
                new StrategyId(entity.getStrategyId()),
                entity.getVersion(),
                entity.getName(),
                entity.getDescription(),
                entity.getScenario(),
                StrategyOperationalStatus.valueOf(entity.getOperationalStatus()),
                ValidationStatus.valueOf(entity.getValidationStatus()),
                entity.getValidationEvidenceRef(),
                StrategyDirection.valueOf(entity.getDirection()),
                new StrategyApplicability(
                        split(entity.getAssetClasses()),
                        splitTimeframes(entity.getTimeframes()),
                        split(entity.getProviders())),
                deserializeInputs(entity.getRequiredInputs()),
                deserializeParameters(entity.getParameters()),
                entity.getResearchRef(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private static String join(java.util.Set<String> values) {
        return values == null || values.isEmpty() ? "" : String.join(SEPARATOR, values);
    }

    private static java.util.Set<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.Set.of();
        }
        return Arrays.stream(raw.split(SEPARATOR))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static java.util.Set<StrategyApplicability.Timeframe> splitTimeframes(String raw) {
        return split(raw).stream()
                .map(StrategyApplicability.Timeframe::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static java.util.Set<RequiredSemanticInput> deserializeInputs(String raw) {
        return split(raw).stream().map(JpaStrategyDefinitionRepository::parseInput)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static RequiredSemanticInput parseInput(String encoded) {
        int separatorIndex = encoded.indexOf(':');
        SemanticInputType type = SemanticInputType.valueOf(encoded.substring(0, separatorIndex));
        return new RequiredSemanticInput(type, encoded.substring(separatorIndex + 1));
    }

    /** Versioned, delimiter-safe parameter encoding for persisted semantics. */
    static String serializeParameters(StrategyParameters parameters) {
        return "v2\n" + parameters.values().stream()
                .map(parameter -> encode(parameter.name()) + "|"
                        + parameter.type() + "|" + encode(canonicalValue(parameter)))
                .collect(Collectors.joining("\n"));
    }

    static StrategyParameters deserializeParameters(String raw) {
        if (raw == null || raw.isBlank()) {
            return StrategyParameters.empty();
        }
        boolean versioned = raw.startsWith("v2\n");
        String encoded = versioned ? raw.substring(3) : raw;
        if (encoded.isBlank()) {
            return StrategyParameters.empty();
        }
        List<StrategyParameter> parsed = new ArrayList<>();
        for (String line : encoded.split("\n")) {
            String[] parts = line.split("\\|", 3);
            StrategyParameter.ParameterType type =
                    StrategyParameter.ParameterType.valueOf(parts[1]);
            String name = versioned ? decode(parts[0]) : parts[0];
            String valueText = versioned ? decode(parts[2]) : parts[2];
            Object value = switch (type) {
                case DECIMAL -> new BigDecimal(valueText);
                case INTEGER -> Long.parseLong(valueText);
                case STRING -> valueText;
                case DURATION -> Duration.parse(valueText);
            };
            parsed.add(new StrategyParameter(name, type, value));
        }
        return new StrategyParameters(parsed);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String canonicalValue(StrategyParameter parameter) {
        return switch (parameter.type()) {
            case DECIMAL -> parameter.decimalValue().toPlainString();
            case INTEGER -> Long.toString(parameter.integerValue());
            case STRING -> parameter.stringValue();
            case DURATION -> parameter.durationValue().toString();
        };
    }
}
