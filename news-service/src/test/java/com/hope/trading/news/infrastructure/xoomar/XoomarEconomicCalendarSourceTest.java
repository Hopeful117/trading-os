package com.hope.trading.news.infrastructure.xoomar;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import com.hope.trading.news.config.XoomarProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

class XoomarEconomicCalendarSourceTest {
    @Test
    void buildsTheConfiguredProviderClient() {
        XoomarProperties properties = new XoomarProperties();

        assertThat(new XoomarEconomicCalendarSource(RestClient.builder(), properties)).isNotNull();
    }

    private static final Instant FROM = Instant.parse("2026-10-05T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-12T00:00:00Z");

    @Test
    void mapsUpcomingAndReleasedEventsWithoutLosingSourceValues() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://xoomar.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo("https://xoomar.test/api/markets/calendar?from=2026-10-05&to=2026-10-12"))
                .andRespond(withSuccess("""
                        {
                          "updatedAt": "2026-10-05T10:00:00Z",
                          "data": [
                            {
                              "id": "xoomar:1",
                              "eventName": "CPI",
                              "sector": "inflation",
                              "currency": "USD",
                              "importance": "high",
                              "scheduledAt": "2026-10-08T12:30:00Z",
                              "unit": "%",
                              "previous": "3.5%",
                              "forecast": null,
                              "actual": null
                            },
                            {
                              "id": null,
                              "eventId": "xoomar:2",
                              "eventName": "FOMC Rate Decision",
                              "type": "event",
                              "currency": "USD",
                              "importance": "medium",
                              "scheduledAt": "2026-10-09T18:00:00Z",
                              "unit": "percent",
                              "previous": 5.25,
                              "forecast": 5.00,
                              "actual": 5.00
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        XoomarEconomicCalendarSource source = new XoomarEconomicCalendarSource(client);

        var events = source.economicEvents(FROM, TO);

        assertThat(events).hasSize(2);
        assertThat(events.getFirst().sourceName()).isEqualTo("xoomar");
        assertThat(events.getFirst().sourceEventId()).isEqualTo("xoomar:1");
        assertThat(events.getFirst().scheduledAt()).isEqualTo(Instant.parse("2026-10-08T12:30:00Z"));
        assertThat(events.getFirst().category()).isEqualTo("inflation");
        assertThat(events.getFirst().impact()).hasToString("HIGH");
        assertThat(events.getFirst().status()).hasToString("SCHEDULED");
        assertThat(events.getFirst().previousValue()).isEqualTo("3.5%");
        assertThat(events.getFirst().unit()).isEqualTo("%");
        assertThat(events.getFirst().consensusValue()).isNull();
        assertThat(events.get(1).category()).isEqualTo("event");
        assertThat(events.get(1).sourceEventId()).isEqualTo("xoomar:2");
        assertThat(events.get(1).status()).hasToString("RELEASED");
        assertThat(events.get(1).previousValue()).isEqualTo("5.25");
        assertThat(events.get(1).actualValue()).isEqualTo("5.0");
        assertThat(events.get(1).unit()).isEqualTo("percent");
        assertThat(events.get(1).sourceUpdatedAt()).isEqualTo(Instant.parse("2026-10-05T10:00:00Z"));
    }

    @Test
    void classifiesRateLimitAsProviderFailure() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://xoomar.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo("https://xoomar.test/api/markets/calendar?from=2026-10-05&to=2026-10-12"))
                .andRespond(withStatus(TOO_MANY_REQUESTS));

        XoomarEconomicCalendarSource source = new XoomarEconomicCalendarSource(client);

        assertThatThrownBy(() -> source.economicEvents(FROM, TO))
                .isInstanceOf(XoomarEconomicCalendarSourceException.class)
                .hasMessageContaining("429");
    }

    @Test
    void skipsMalformedRecordsAndMapsUnknownImportance() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://xoomar.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo("https://xoomar.test/api/markets/calendar?from=2026-10-05&to=2026-10-12"))
                .andRespond(withSuccess("""
                        {"data": [
                          {"id":"valid","eventName":"Unknown event","importance":"critical","scheduledAt":"2026-10-10T00:00:00Z"},
                          {"id":"invalid","eventName":"Broken event","importance":"low","scheduledAt":"not-a-date"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var events = new XoomarEconomicCalendarSource(client).economicEvents(FROM, TO);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().impact()).hasToString("UNKNOWN");
    }

    @Test
    void rejectsAnUnboundedCalendarWindowBeforeCallingProvider() {
        RestClient client = RestClient.builder().baseUrl("https://xoomar.test").build();
        Instant to = FROM.plusSeconds(32L * 86400L);
        XoomarEconomicCalendarSource source = new XoomarEconomicCalendarSource(client);

        assertThatThrownBy(() -> source.economicEvents(FROM, to))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("31 days");
    }

    @Test
    void classifiesServerErrorsAsProviderFailure() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://xoomar.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo("https://xoomar.test/api/markets/calendar?from=2026-10-05&to=2026-10-12"))
                .andRespond(withServerError());
        XoomarEconomicCalendarSource source = new XoomarEconomicCalendarSource(client);

        assertThatThrownBy(() -> source.economicEvents(FROM, TO))
                .isInstanceOf(XoomarEconomicCalendarSourceException.class)
                .hasMessageContaining("500");
    }
}
