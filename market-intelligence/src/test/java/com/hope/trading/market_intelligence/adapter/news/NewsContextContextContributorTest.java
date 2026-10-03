package com.hope.trading.market_intelligence.adapter.news;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.ContextSectionStatus;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsContextContextContributorTest {
    @Test
    void exposesProviderUnavailableWithoutFabricatingFacts() {
        NewsClient client = mock(NewsClient.class);
        UUID marketId = UUID.randomUUID();
        when(client.findContext(marketId)).thenReturn(new NewsClient.NewsContextResponse(
                "UNAVAILABLE", null, null, null, null,
                "No production news provider is configured"));

        NewsContextContextContributor contributor = new NewsContextContextContributor(client);

        assertThat(contributor.contribute(new IntelligenceAnalysisRequest(
                UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE, "context"))
                .status()).isEqualTo(ContextSectionStatus.UNAVAILABLE);
    }
}
