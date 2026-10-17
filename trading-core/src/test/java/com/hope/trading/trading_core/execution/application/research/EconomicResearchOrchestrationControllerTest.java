package com.hope.trading.trading_core.execution.application.research;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EconomicResearchOrchestrationControllerTest {
    @Test
    void delegatesBoundedResearchRequestsToMarketIntelligence() {
        var client = mock(EconomicResearchMarketIntelligenceClient.class);
        var response = new EconomicResearchMarketIntelligenceClient.EvaluationResponse(
                "candidates", "results", "manifest", "readme", "fingerprint");
        when(client.evaluate(any())).thenReturn(response);
        var controller = new EconomicResearchOrchestrationController(client);

        var actual = controller.orchestrate(new EconomicResearchOrchestrationController.OrchestrationRequest(
                "candidates", "2026-10-17T10:00:00Z", "cost-v1"));

        assertThat(actual.getBody()).isEqualTo(response);
        verify(client).evaluate(new EconomicResearchMarketIntelligenceClient.EvaluationRequest(
                "candidates", "2026-10-17T10:00:00Z", "cost-v1"));
    }
}
