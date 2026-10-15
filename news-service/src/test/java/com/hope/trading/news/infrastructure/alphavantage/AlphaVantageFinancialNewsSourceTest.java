package com.hope.trading.news.infrastructure.alphavantage;

import com.hope.trading.news.config.AlphaVantageProperties;
import com.hope.trading.news.domain.NewsAvailability;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.http.HttpMethod.GET;

class AlphaVantageFinancialNewsSourceTest {
    private static final Instant FROM = Instant.parse("2026-10-05T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-05T12:00:00Z");

    @Test
    void mapsProviderFactsWithoutTurningSentimentIntoImpact() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://alphavantage.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> {
            assertThat(request.getMethod()).isEqualTo(GET);
            assertThat(request.getURI().getPath()).isEqualTo("/query");
            assertThat(request.getURI().getQuery()).contains("function=NEWS_SENTIMENT");
            assertThat(request.getURI().getQuery()).contains("time_from=20261005T0000");
            assertThat(request.getURI().getQuery()).contains("time_to=20261005T1200");
            assertThat(request.getURI().getQuery()).contains("sort=LATEST");
        }).andRespond(withSuccess("""
                {
                  "feed": [{
                    "title": "Bitcoin market update",
                    "url": "https://publisher.test/bitcoin-1",
                    "time_published": "20261005T103000",
                    "authors": ["Reporter"],
                    "summary": "A concise market report.",
                    "source": "Publisher One",
                    "category_within_source": "Markets",
                    "topics": [{"topic": "blockchain", "relevance_score": "0.9"}],
                    "ticker_sentiment": [{"ticker": "CRYPTO:BTC", "ticker_sentiment_score": "0.8"}],
                    "overall_sentiment_score": "0.7",
                    "overall_sentiment_label": "Bullish"
                  }]
                }
                """, MediaType.APPLICATION_JSON));

        var source = new AlphaVantageFinancialNewsSource(builder.build(), properties());
        var items = source.financialNews(FROM, TO);

        assertThat(items).hasSize(1);
        var item = items.getFirst();
        assertThat(item.sourceName()).isEqualTo("alpha-vantage");
        assertThat(item.sourceItemId()).startsWith("url:").hasSize(68);
        assertThat(item.publisher()).isEqualTo("Publisher One");
        assertThat(item.publishedAt()).isEqualTo(Instant.parse("2026-10-05T10:30:00Z"));
        assertThat(item.summary()).isEqualTo("A concise market report.");
        assertThat(item.categories()).containsExactly("Markets", "blockchain");
        assertThat(item.currencies()).containsExactly("BTC");
        assertThat(item.marketIds()).isEmpty();
        assertThat(item.impact()).hasToString("UNKNOWN");
    }

    @Test
    void skipsRecordsWithoutUsablePublicationTimesAndReportsIncomplete() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://alphavantage.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/query"))
                .andRespond(withSuccess("""
                        {"feed": [
                          {"title":"valid", "url":"https://publisher.test/valid", "time_published":"20261005T103000"},
                          {"title":"missing time", "url":"https://publisher.test/missing"},
                          {"title":"bad time", "url":"https://publisher.test/bad", "time_published":"not-a-time"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var source = new AlphaVantageFinancialNewsSource(builder.build(), properties());
        var items = source.financialNews(FROM, TO);

        assertThat(items).hasSize(1);
        assertThat(source.lastRejectedCount()).isEqualTo(2);
        assertThat(source.availability()).isEqualTo(NewsAvailability.INCOMPLETE);
    }

    @Test
    void usesDeterministicFingerprintWhenProviderOmitsUrl() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://alphavantage.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String payload = """
                {"feed": [{"title":"No URL article", "time_published":"20261005T103000", "source":"Wire", "summary":"Excerpt"}]}
                """;
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/query"))
                .andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/query"))
                .andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));
        var source = new AlphaVantageFinancialNewsSource(builder.build(), properties());

        String first = source.financialNews(FROM, TO).getFirst().sourceItemId();
        String second = source.financialNews(FROM, TO).getFirst().sourceItemId();

        assertThat(first).isEqualTo(second).startsWith("fingerprint:");
    }

    @Test
    void mapsQuotaResponseToUnavailableWithoutReturningAnEmptyCatalog() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://alphavantage.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/query"))
                .andRespond(withSuccess("{\"Note\":\"Thank you for using Alpha Vantage!\"}",
                        MediaType.APPLICATION_JSON));
        var source = new AlphaVantageFinancialNewsSource(builder.build(), properties());

        assertThatThrownBy(() -> source.financialNews(FROM, TO))
                .isInstanceOf(AlphaVantageFinancialNewsSourceException.class)
                .extracting(exception -> ((AlphaVantageFinancialNewsSourceException) exception).availability())
                .isEqualTo(NewsAvailability.UNAVAILABLE);
        assertThat(source.availability()).isEqualTo(NewsAvailability.UNAVAILABLE);
        assertThat(source.availabilityMessage()).isEqualTo("Alpha Vantage quota or rate limit response");
    }

    @Test
    void boundsUrlBasedIdentityAndDoesNotExposeTheApiKeyInFailures() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://alphavantage.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String longUrl = "https://publisher.test/" + "a".repeat(980);
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/query"))
                .andRespond(withSuccess("""
                        {"feed": [{"title":"Long URL article", "url":"%s", "time_published":"20261005T103000"}]}
                        """.formatted(longUrl), MediaType.APPLICATION_JSON));
        var source = new AlphaVantageFinancialNewsSource(builder.build(), properties());

        var item = source.financialNews(FROM, TO).getFirst();

        assertThat(item.sourceItemId()).startsWith("url:").hasSize(68);

        RestClient.Builder failingBuilder = RestClient.builder().baseUrl("https://alphavantage.test");
        MockRestServiceServer failingServer = MockRestServiceServer.bindTo(failingBuilder).build();
        failingServer.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/query"))
                .andRespond(withServerError());
        var failingSource = new AlphaVantageFinancialNewsSource(failingBuilder.build(), properties());

        assertThatThrownBy(() -> failingSource.financialNews(FROM, TO))
                .isInstanceOf(AlphaVantageFinancialNewsSourceException.class)
                .hasMessageNotContaining("test-key")
                .hasNoCause();
    }

    @Test
    void acceptsAnEmptyFeedAsAnAvailableSuccessfulResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://alphavantage.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/query"))
                .andRespond(withSuccess("{\"feed\":[]}", MediaType.APPLICATION_JSON));

        var source = new AlphaVantageFinancialNewsSource(builder.build(), properties());

        assertThat(source.financialNews(FROM, TO)).isEmpty();
        assertThat(source.availability()).isEqualTo(NewsAvailability.AVAILABLE);
    }

    private static AlphaVantageProperties properties() {
        AlphaVantageProperties properties = new AlphaVantageProperties();
        properties.setApiKey("test-key");
        return properties;
    }
}
