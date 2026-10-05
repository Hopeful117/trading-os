package com.hope.trading.news.infrastructure.xoomar;

import com.hope.trading.news.application.EconomicCalendarSourcePort;
import com.hope.trading.news.config.XoomarProperties;
import com.hope.trading.news.domain.EconomicEvent;
import com.hope.trading.news.domain.EconomicEventStatus;
import com.hope.trading.news.domain.ImpactLevel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "news.xoomar", name = "enabled", havingValue = "true")
public class XoomarEconomicCalendarSource implements EconomicCalendarSourcePort {
    private static final String SOURCE_NAME = "xoomar";
    private static final String NORMALIZATION_VERSION = "xoomar-v1";

    private final RestClient client;

    @Autowired
    public XoomarEconomicCalendarSource(RestClient.Builder restClientBuilder,
                                       XoomarProperties properties) {
        properties.validate();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.client = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl().toString())
                .build();
    }

    XoomarEconomicCalendarSource(RestClient client) {
        this.client = client;
    }

    @Override
    public List<EconomicEvent> economicEvents(Instant from, Instant to) {
        try {
            validateWindow(from, to);
            XoomarCalendarResponse response = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/markets/calendar")
                            .queryParam("from", from.atOffset(ZoneOffset.UTC).toLocalDate())
                            .queryParam("to", to.atOffset(ZoneOffset.UTC).toLocalDate())
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, responseMessage) -> {
                        throw new XoomarEconomicCalendarSourceException(
                                "XOOMAR economic calendar returned " + responseMessage.getStatusCode(), null);
                    })
                    .body(XoomarCalendarResponse.class);
            if (response == null || response.data() == null) {
                return List.of();
            }
            Instant fetchedAt = Instant.now();
            Instant sourceUpdatedAt = parseInstant(response.updatedAt());
            return response.data().stream()
                    .filter(event -> event != null && hasText(event.sourceEventId()) && hasText(event.eventName()))
                    .map(event -> toDomain(event, sourceUpdatedAt, fetchedAt))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IllegalArgumentException | XoomarEconomicCalendarSourceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new XoomarEconomicCalendarSourceException(
                    "Unable to retrieve the XOOMAR economic calendar", exception);
        }
    }

    private EconomicEvent toDomain(XoomarCalendarEvent event,
                                   Instant sourceUpdatedAt,
                                   Instant fetchedAt) {
        Instant scheduledAt = parseInstant(event.scheduledAt());
        if (scheduledAt == null) {
            return null;
        }
        return new EconomicEvent(
                null,
                SOURCE_NAME,
                event.sourceEventId(),
                event.eventName(),
                firstText(event.sector(), event.type()),
                scheduledAt,
                null,
                event.currency() == null || event.currency().isBlank()
                        ? List.of() : List.of(event.currency()),
                List.of(),
                mapImpact(event.importance()),
                value(event.actual()) == null ? EconomicEventStatus.SCHEDULED : EconomicEventStatus.RELEASED,
                value(event.previous()),
                value(event.forecast()),
                value(event.actual()),
                event.unit(),
                sourceUpdatedAt,
                fetchedAt,
                NORMALIZATION_VERSION);
    }

    private static ImpactLevel mapImpact(String value) {
        if (value == null) {
            return ImpactLevel.UNKNOWN;
        }
        return switch (value.toLowerCase()) {
            case "low" -> ImpactLevel.LOW;
            case "medium", "med" -> ImpactLevel.MEDIUM;
            case "high" -> ImpactLevel.HIGH;
            default -> ImpactLevel.UNKNOWN;
        };
    }

    private static String value(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isTextual() ? value.textValue() : value.toString();
    }

    private static Instant parseInstant(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (Exception ignoredOffset) {
                try {
                    return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
                } catch (Exception ignoredLocal) {
                    try {
                        return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
                    } catch (Exception ignoredDate) {
                        return null;
                    }
                }
            }
        }
    }

    private static String firstText(String first, String second) {
        return hasText(first) ? first : second;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void validateWindow(Instant from, Instant to) {
        if (from == null || to == null || from.isAfter(to)
                || from.plusSeconds(31L * 86400L).isBefore(to)) {
            throw new IllegalArgumentException("XOOMAR calendar window must be between 0 and 31 days");
        }
    }

    public record XoomarCalendarResponse(
            List<XoomarCalendarEvent> data,
            String updatedAt
    ) {
    }

    public record XoomarCalendarEvent(
            String id,
            String eventId,
            String eventName,
            String countryCode,
            String currency,
            String importance,
            String type,
            String sector,
            String scheduledAt,
            String unit,
            JsonNode previous,
            JsonNode forecast,
            JsonNode actual
    ) {
        String sourceEventId() {
            return hasText(id) ? id : eventId;
        }
    }
}
