package com.hope.trading.news.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "news.providers.alpha-vantage")
public class AlphaVantageProperties {
    private boolean enabled;
    private String apiKey = "";
    private boolean pollingEnabled;
    private URI baseUrl = URI.create("https://www.alphavantage.co");
    private Duration pollInterval = Duration.ofHours(6);
    private Duration lookback = Duration.ofHours(48);
    private int limit = 50;
    private Duration timeout = Duration.ofSeconds(15);
    private List<String> topics = List.of("blockchain", "financial_markets", "economy_monetary",
            "economy_macro", "finance");
    private List<String> tickers = List.of("CRYPTO:BTC", "CRYPTO:ETH", "FOREX:USD");

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String apiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public boolean pollingEnabled() {
        return pollingEnabled;
    }

    public void setPollingEnabled(boolean pollingEnabled) {
        this.pollingEnabled = pollingEnabled;
    }

    public URI baseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration pollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public Duration lookback() {
        return lookback;
    }

    public void setLookback(Duration lookback) {
        this.lookback = lookback;
    }

    public int limit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public Duration timeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public List<String> topics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics == null ? List.of() : List.copyOf(topics);
    }

    public List<String> tickers() {
        return tickers;
    }

    public void setTickers(List<String> tickers) {
        this.tickers = tickers == null ? List.of() : List.copyOf(tickers);
    }

    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Alpha Vantage API key must be configured when enabled");
        }
        if (baseUrl == null || pollInterval == null || pollInterval.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("Alpha Vantage base URL and poll interval are invalid");
        }
        if (lookback == null || lookback.isNegative() || lookback.compareTo(Duration.ofDays(31)) > 0) {
            throw new IllegalArgumentException("Alpha Vantage lookback must be between 0 and 31 days");
        }
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Alpha Vantage limit must be between 1 and 1000");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Alpha Vantage timeout must be positive");
        }
    }
}
