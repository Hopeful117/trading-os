package com.hope.trading.market_data.ctrader.authentication;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;
import java.net.URI;
import java.util.Map;
import java.util.List;
import java.time.Instant;

@ConfigurationProperties(prefix = "ctrader.market-data")
public record CTraderMarketDataProperties(
        CTraderEnvironment environment,
        String clientId,
        String clientSecret,
        String authorizationEndpoint,
        String redirectUri,
        String tokenEndpoint,
        String websocketEndpoint,
        Duration requestTimeout,
        Duration authorizationStateTtl,
        Map<String, String> symbolMappings,
        Map<String, String> symbolProductTypes,
        Map<String, String> symbolContractMultipliers,
         Map<String, String> symbolContractSourceVersions,
         List<ContractReference> contractReferences,
         long externalAccountId,
         int maxTrendbarPages,
         int maxTrendbarRows,
         boolean symbolDiagnosticsEnabled,
         long symbolDiagnosticsAccountId,
         long symbolDiagnosticsSymbolId) {
    public CTraderMarketDataProperties(CTraderEnvironment environment, String clientId, String clientSecret,
                                       String websocketEndpoint, Duration requestTimeout,
                                       Map<String, String> symbolMappings) {
        this(environment, clientId, clientSecret, null, null, null, websocketEndpoint, requestTimeout,
                Duration.ofMinutes(10), symbolMappings, Map.of(), Map.of(), Map.of(), List.of(), 0L,
                8, 720, false, 49411967L, 41L);
    }

    public CTraderMarketDataProperties(CTraderEnvironment environment, String clientId, String clientSecret,
                                       String tokenEndpoint, String websocketEndpoint, Duration requestTimeout,
                                       Map<String, String> symbolMappings, int maxTrendbarPages, int maxTrendbarRows) {
        this(environment, clientId, clientSecret, null, null, tokenEndpoint, websocketEndpoint, requestTimeout,
                Duration.ofMinutes(10), symbolMappings, Map.of(), Map.of(), Map.of(), List.of(), 0L,
                maxTrendbarPages, maxTrendbarRows, false, 49411967L, 41L);
    }

    public CTraderMarketDataProperties(CTraderEnvironment environment, String clientId, String clientSecret,
                                       String authorizationEndpoint, String redirectUri, String tokenEndpoint,
                                       String websocketEndpoint, Duration requestTimeout, Duration authorizationStateTtl,
                                       Map<String, String> symbolMappings, Map<String, String> symbolProductTypes,
                                       int maxTrendbarPages, int maxTrendbarRows) {
        this(environment, clientId, clientSecret, authorizationEndpoint, redirectUri, tokenEndpoint,
                websocketEndpoint, requestTimeout, authorizationStateTtl, symbolMappings, symbolProductTypes,
                Map.of(), Map.of(), List.of(), 49411967L, maxTrendbarPages, maxTrendbarRows, false, 49411967L, 41L);
    }

    public CTraderMarketDataProperties(CTraderEnvironment environment, String clientId, String clientSecret,
                                       String authorizationEndpoint, String redirectUri, String tokenEndpoint,
                                       String websocketEndpoint, Duration requestTimeout, Duration authorizationStateTtl,
                                       Map<String, String> symbolMappings, Map<String, String> symbolProductTypes,
                                       Map<String, String> symbolContractMultipliers,
                                        Map<String, String> symbolContractSourceVersions,
                                        int maxTrendbarPages, int maxTrendbarRows) {
        this(environment, clientId, clientSecret, authorizationEndpoint, redirectUri, tokenEndpoint,
                websocketEndpoint, requestTimeout, authorizationStateTtl, symbolMappings, symbolProductTypes,
                symbolContractMultipliers, symbolContractSourceVersions, List.of(), 49411967L, maxTrendbarPages, maxTrendbarRows,
                false, 49411967L, 41L);
    }

    public CTraderMarketDataProperties(CTraderEnvironment environment, String clientId, String clientSecret,
                                       String authorizationEndpoint, String redirectUri, String tokenEndpoint,
                                       String websocketEndpoint, Duration requestTimeout, Duration authorizationStateTtl,
                                       Map<String, String> symbolMappings, Map<String, String> symbolProductTypes,
                                       Map<String, String> symbolContractMultipliers,
                                       Map<String, String> symbolContractSourceVersions,
                                       int maxTrendbarPages, int maxTrendbarRows, boolean symbolDiagnosticsEnabled,
                                       long symbolDiagnosticsAccountId, long symbolDiagnosticsSymbolId) {
        this(environment, clientId, clientSecret, authorizationEndpoint, redirectUri, tokenEndpoint,
                websocketEndpoint, requestTimeout, authorizationStateTtl, symbolMappings, symbolProductTypes,
                symbolContractMultipliers, symbolContractSourceVersions, List.of(), symbolDiagnosticsAccountId,
                maxTrendbarPages, maxTrendbarRows, symbolDiagnosticsEnabled,
                symbolDiagnosticsAccountId, symbolDiagnosticsSymbolId);
    }

    @ConstructorBinding
    public CTraderMarketDataProperties {
        environment = environment == null ? CTraderEnvironment.DEMO : environment;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(15) : requestTimeout;
        authorizationStateTtl = authorizationStateTtl == null ? Duration.ofMinutes(10) : authorizationStateTtl;
        if (authorizationStateTtl.isZero() || authorizationStateTtl.isNegative()) {
            throw new IllegalArgumentException("cTrader authorization state TTL must be positive");
        }
        symbolMappings = symbolMappings == null ? Map.of() : Map.copyOf(symbolMappings);
        symbolProductTypes = symbolProductTypes == null ? Map.of() : Map.copyOf(symbolProductTypes);
         symbolContractMultipliers = symbolContractMultipliers == null ? Map.of() : Map.copyOf(symbolContractMultipliers);
         symbolContractSourceVersions = symbolContractSourceVersions == null ? Map.of() : Map.copyOf(symbolContractSourceVersions);
        contractReferences = contractReferences == null ? List.of() : List.copyOf(contractReferences);
        if (maxTrendbarPages < 1 || maxTrendbarRows < 1) {
            throw new IllegalArgumentException("cTrader trendbar bounds must be positive");
        }
        if (tokenEndpoint != null && !tokenEndpoint.isBlank()
                && !"https".equalsIgnoreCase(URI.create(tokenEndpoint).getScheme())) {
            throw new IllegalArgumentException("cTrader token endpoint must use HTTPS");
        }
        if (authorizationEndpoint != null && !authorizationEndpoint.isBlank()
                && !"https".equalsIgnoreCase(URI.create(authorizationEndpoint).getScheme())) {
            throw new IllegalArgumentException("cTrader authorization endpoint must use HTTPS");
        }
        if (environment != null && websocketEndpoint != null && !websocketEndpoint.isBlank()) {
            if (!"wss".equalsIgnoreCase(URI.create(websocketEndpoint).getScheme())) {
                throw new IllegalArgumentException("cTrader websocket endpoint must use WSS");
            }
            String endpoint = websocketEndpoint.toLowerCase();
            boolean live = endpoint.contains("live.ctraderapi.com");
            boolean demo = endpoint.contains("demo.ctraderapi.com");
            if ((environment == CTraderEnvironment.LIVE && demo)
                    || (environment == CTraderEnvironment.DEMO && live)) {
                throw new IllegalArgumentException("cTrader environment and websocket endpoint do not match");
            }
        }
    }

    public record ContractReference(
            String provider,
            long accountExternalId,
            CTraderEnvironment environment,
            String providerSymbol,
            String canonicalInstrument,
            String productType,
            String quantityUnit,
            java.math.BigDecimal contractMultiplier,
            String sourceId,
            Long sourceVersion,
            Instant effectiveFrom,
            Instant effectiveTo,
            String reviewStatus) {
        public boolean acceptedAt(Instant now) {
            return "CTRADER".equalsIgnoreCase(provider)
                    && "ACCEPTED".equalsIgnoreCase(reviewStatus)
                    && accountExternalId > 0
                    && environment != null
                    && providerSymbol != null && !providerSymbol.isBlank()
                    && canonicalInstrument != null && !canonicalInstrument.isBlank()
                    && productType != null && !productType.isBlank()
                    && quantityUnit != null && !quantityUnit.isBlank()
                    && contractMultiplier != null && contractMultiplier.signum() > 0
                    && sourceId != null && !sourceId.isBlank()
                    && (sourceVersion != null || effectiveFrom != null)
                    && (effectiveFrom == null || !effectiveFrom.isAfter(now))
                    && (effectiveTo == null || effectiveTo.isAfter(now));
        }
    }
}
