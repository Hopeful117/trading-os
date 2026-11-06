package com.hope.trading.market_intelligence.adapter.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.domain.IntelligenceContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiEngineAnalysisRequestTest {
    @Test
    void serializesAuthorizedContextInThePythonContractShape() throws Exception {
        IntelligenceContext context = new IntelligenceContext(Map.of());

        var wire = AiEngineAnalysisRequest.AuthorizedContextWire.from(context);
        var json = new ObjectMapper().valueToTree(wire);

        assertThat(json.get("fingerprint").isTextual()).isTrue();
        assertThat(json.get("fingerprint").asText()).hasSize(64);
        assertThat(json.get("sections").isObject()).isTrue();
    }
}
