package com.hope.trading.news.infrastructure.alphavantage;

import com.hope.trading.news.application.FinancialNewsSourcePort;
import com.hope.trading.news.config.AlphaVantageProperties;
import com.hope.trading.news.domain.FinancialNewsItem;
import com.hope.trading.news.domain.ImpactLevel;
import com.hope.trading.news.domain.NewsAvailability;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "news.provider", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "news.providers.alpha-vantage", name = "enabled", havingValue = "true")
public class AlphaVantageFinancialNewsSource implements FinancialNewsSourcePort {
    static final String SOURCE_NAME = "alpha-vantage";
    static final String NORMALIZATION_VERSION = "alpha-vantage-v1";
    private static final String FUNCTION = "NEWS_SENTIMENT";
    private static final DateTimeFormatter REQUEST_TIME =
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmm").withLocale(Locale.ROOT);
    private static final DateTimeFormatter RESPONSE_TIME =
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss").withLocale(Locale.ROOT);
    private static final DateTimeFormatter RESPONSE_TIME_WITHOUT_SECONDS =
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmm").withLocale(Locale.ROOT);

    private final RestClient client;
    private final AlphaVantageProperties properties;
    private volatile NewsAvailability lastAvailability = NewsAvailability.AVAILABLE;
    private volatile String lastAvailabilityMessage;
    private volatile int lastRejectedCount;

    @Autowired
    public AlphaVantageFinancialNewsSource(RestClient.Builder restClientBuilder,
                                           AlphaVantageProperties properties) {
        properties.validate();
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = properties.timeout();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.client = restClientBuilder.requestFactory(requestFactory)
                .baseUrl(properties.baseUrl().toString())
                .build();
    }

    AlphaVantageFinancialNewsSource(RestClient client, AlphaVantageProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public List<FinancialNewsItem> financialNews(Instant from, Instant to) {
        validateWindow(from, to);
        lastRejectedCount = 0;
        lastAvailability = NewsAvailability.AVAILABLE;
        lastAvailabilityMessage = null;
        Instant fetchedAt = Instant.now();
        try {
            JsonNode response = client.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/query")
                                .queryParam("function", FUNCTION)
                                .queryParam("apikey", properties.apiKey())
                                .queryParam("time_from", formatTime(from))
                                .queryParam("time_to", formatTime(to))
                                .queryParam("sort", "LATEST")
                                .queryParam("limit", properties.limit());
                        if (!properties.topics().isEmpty()) {
                            builder.queryParam("topics", String.join(",", properties.topics()));
                        }
                        if (!properties.tickers().isEmpty()) {
                            builder.queryParam("tickers", String.join(",", properties.tickers()));
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, responseMessage) ->
                            throwSourceFailure("Alpha Vantage returned " + responseMessage.getStatusCode(),
                                    NewsAvailability.UNAVAILABLE, null))
                    .body(JsonNode.class);
            validateProviderResponse(response);
            List<FinancialNewsItem> result = new ArrayList<>();
            for (JsonNode article : response.path("feed")) {
                try {
                    result.add(toDomain(article, fetchedAt));
                } catch (IllegalArgumentException exception) {
                    lastRejectedCount++;
                }
            }
            if (lastRejectedCount > 0) {
                lastAvailability = NewsAvailability.INCOMPLETE;
            }
            return result;
        } catch (AlphaVantageFinancialNewsSourceException exception) {
            lastAvailability = exception.availability();
            lastAvailabilityMessage = exception.getMessage();
            throw exception;
        } catch (RuntimeException ignored) {
            lastAvailability = NewsAvailability.UNAVAILABLE;
            lastAvailabilityMessage = "Unable to retrieve Alpha Vantage financial news";
            throw new AlphaVantageFinancialNewsSourceException(
                    lastAvailabilityMessage, NewsAvailability.UNAVAILABLE, null);
        }
    }

    @Override
    public NewsAvailability availability() {
        return lastAvailability;
    }

    @Override
    public String availabilityMessage() {
        return lastAvailabilityMessage;
    }

    public int lastRejectedCount() {
        return lastRejectedCount;
    }

    private FinancialNewsItem toDomain(JsonNode article, Instant fetchedAt) {
        String title = text(article, "title");
        Instant publishedAt = parsePublishedAt(text(article, "time_published"));
        if (title == null) {
            throw new IllegalArgumentException("Alpha Vantage article title is missing");
        }
        if (publishedAt == null) {
            throw new IllegalArgumentException("Alpha Vantage article publication time is missing or malformed");
        }
        String canonicalUrl = text(article, "url");
        String publisher = text(article, "source");
        String summary = text(article, "summary");
        String sourceItemId = stableSourceItemId(canonicalUrl, publisher, title, publishedAt, summary);
        return new FinancialNewsItem(
                null,
                SOURCE_NAME,
                sourceItemId,
                title,
                summary,
                canonicalUrl,
                publishedAt,
                publisher,
                categories(article),
                currencies(article),
                List.of(),
                ImpactLevel.UNKNOWN,
                null,
                fetchedAt,
                NORMALIZATION_VERSION);
    }

    private static List<String> categories(JsonNode article) {
        Set<String> values = new LinkedHashSet<>();
        String sourceCategory = text(article, "category_within_source");
        if (sourceCategory != null) {
            values.add(sourceCategory);
        }
        JsonNode topics = article.path("topics");
        if (topics.isArray()) {
            for (JsonNode topic : topics) {
                String value = text(topic, "topic");
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    private static List<String> currencies(JsonNode article) {
        Set<String> values = new LinkedHashSet<>();
        JsonNode tickers = article.path("ticker_sentiment");
        if (tickers.isArray()) {
            for (JsonNode ticker : tickers) {
                String value = text(ticker, "ticker");
                if (value == null) {
                    continue;
                }
                if (value.startsWith("CRYPTO:")) {
                    values.add(value.substring("CRYPTO:".length()).toUpperCase(Locale.ROOT));
                } else if (value.startsWith("FOREX:")) {
                    values.add(value.substring("FOREX:".length()).toUpperCase(Locale.ROOT));
                }
            }
        }
        return List.copyOf(values);
    }

    private static void validateProviderResponse(JsonNode response) {
        if (response == null || !response.isObject()) {
            throwSourceFailure("Alpha Vantage returned an invalid response", NewsAvailability.INCOMPLETE, null);
        }
        if (response.has("Note") || response.has("Information")) {
            throwSourceFailure("Alpha Vantage quota or rate limit response", NewsAvailability.UNAVAILABLE, null);
        }
        if (response.has("Error Message")) {
            throwSourceFailure("Alpha Vantage rejected the request", NewsAvailability.UNAVAILABLE, null);
        }
        if (!response.path("feed").isArray()) {
            throwSourceFailure("Alpha Vantage response did not contain a feed", NewsAvailability.INCOMPLETE, null);
        }
    }

    private static void validateWindow(Instant from, Instant to) {
        if (from == null || to == null || from.isAfter(to)
                || from.plus(Duration.ofDays(31)).isBefore(to)) {
            throw new IllegalArgumentException("Alpha Vantage news window must be between 0 and 31 days");
        }
    }

    private static String formatTime(Instant instant) {
        return REQUEST_TIME.format(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    private static Instant parsePublishedAt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, RESPONSE_TIME).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            try {
                return LocalDateTime.parse(value, RESPONSE_TIME_WITHOUT_SECONDS).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private static String stableSourceItemId(String url, String publisher, String title,
                                             Instant publishedAt, String summary) {
        if (url != null) {
            return "url:" + sha256(url);
        }
        return "fingerprint:" + sha256(encode(publisher) + encode(title)
                + encode(publishedAt.toString()) + encode(summary));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static String encode(String value) {
        String normalized = value == null ? "" : value;
        return normalized.length() + ":" + normalized;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                result.append(String.format("%02x", part));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void throwSourceFailure(String message, NewsAvailability availability, Throwable cause) {
        throw new AlphaVantageFinancialNewsSourceException(message, availability, cause);
    }
}
