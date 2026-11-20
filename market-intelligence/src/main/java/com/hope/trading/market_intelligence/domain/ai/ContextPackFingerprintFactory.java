package com.hope.trading.market_intelligence.domain.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.hope.trading.market_intelligence.domain.ContextSection;
import com.hope.trading.market_intelligence.domain.ContextSectionType;
import com.hope.trading.market_intelligence.domain.IntelligenceContext;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.opportunity.MarketThesisReference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Creates stable fingerprints for the accepted ContextPack contract. */
public final class ContextPackFingerprintFactory {
    private static final ObjectMapper MAPPER = mapper();

    private ContextPackFingerprintFactory() {
    }

    public static ArtifactFingerprint fingerprint(ContextPack pack) {
        List<String> inputs = new ArrayList<>();
        inputs.add("schema=" + pack.schemaVersion());
        inputs.add("mode=" + pack.mode());
        inputs.add("authorization=" + pack.authorization());
        inputs.add("market=" + pack.marketId());
        inputs.add("opportunity=" + pack.opportunity());
        inputs.add("thesis=" + pack.thesis());
        inputs.add("cutoff=" + pack.evaluationCutoff());
        inputs.add("quality=" + pack.quality());
        pack.authorizedEvidenceReferences().stream().map(UUID::toString).sorted()
                .map(value -> "evidence=" + value).forEach(inputs::add);
        pack.diagnostics().stream().sorted().map(value -> "diagnostic=" + value).forEach(inputs::add);
        pack.sections().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ContextSectionType::name)))
                .map(entry -> "section=" + entry.getKey() + ":" + serialize(entry.getValue()))
                .forEach(inputs::add);
        return ArtifactFingerprint.ofInputs(inputs);
    }

    private static ObjectMapper mapper() {
        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        SimpleModule module = new SimpleModule();
        module.addSerializer((Class) Set.class, new SetSerializer());
        return mapper.registerModule(module);
    }

    private static String serialize(ContextSection section) {
        try {
            return MAPPER.writeValueAsString(section);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Context section cannot be canonicalized", exception);
        }
    }

    private static final class SetSerializer extends JsonSerializer<Set<?>> {
        @Override
        public void serialize(Set<?> value, JsonGenerator generator, SerializerProvider serializers)
                throws IOException {
            List<JsonNode> nodes = value.stream()
                    .map(item -> MAPPER.<JsonNode>valueToTree(item))
                    .sorted(Comparator.comparing(JsonNode::toString))
                    .toList();
            generator.writeStartArray();
            for (JsonNode node : nodes) {
                generator.writeTree(node);
            }
            generator.writeEndArray();
        }
    }
}
