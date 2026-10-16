package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.outcome.MarketOutcomeEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalMarketOutcomeControllerTest {
    private final InternalMarketOutcomeController controller =
            new InternalMarketOutcomeController(mock(MarketOutcomeEvaluationService.class));

    @Test
    void rejectsMissingRequiredFieldsAsBadRequest() {
        assertThat(controller.evaluate(new MarketOutcomeEvaluationRequestDto(
                null, null, Duration.ofHours(1), null)).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void rejectsHorizonBeyondMarketDataRangeAsBadRequest() {
        assertThat(controller.evaluate(new MarketOutcomeEvaluationRequestDto(
                java.util.UUID.randomUUID(), java.time.Instant.now(), Duration.ofDays(8), null))
                .getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void mapsInvalidJsonPayloadsToBadRequest() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/internal/v1/market-outcomes/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marketId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest());
    }
}
