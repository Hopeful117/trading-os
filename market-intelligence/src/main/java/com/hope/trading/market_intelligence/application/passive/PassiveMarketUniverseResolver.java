package com.hope.trading.market_intelligence.application.passive;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.config.PassiveMarketUniverseProperties;
import com.hope.trading.market_intelligence.config.PassiveMarketUniverseProperties.Entry;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PassiveMarketUniverseResolver {
    static final String FINGERPRINT_ALGORITHM = "SHA-256:passive-market-universe-v1";
    private static final Comparator<PassiveMarketEntry> ENTRY_ORDER = Comparator
            .comparing((PassiveMarketEntry entry) -> entry.marketId().toString())
            .thenComparing(PassiveMarketEntry::provider)
            .thenComparing(PassiveMarketEntry::productType)
            .thenComparing(entry -> entry.cadence().toString())
            .thenComparing(entry -> entry.minimumEvidenceProfile().name());

    private final PassiveMarketUniverseProperties properties;
    private final MarketDataClient marketDataClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PassiveMarketUniverseResolver(
            PassiveMarketUniverseProperties properties,
            MarketDataClient marketDataClient,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.properties = properties;
        this.marketDataClient = marketDataClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public PassiveMarketUniverse resolve() {
        properties.validate();
        Instant resolvedAt = clock.instant();
        String configurationVersion = properties.getVersion();
        List<Entry> configured = properties.getEntries();
        validateDuplicateConfigurationEntries(configured);
        if (configured.isEmpty()) {
            return universe(configurationVersion, resolvedAt, List.of(), List.of());
        }

        List<PassiveMarketUniverseDiagnostic> diagnostics = new ArrayList<>();
        List<Entry> enabledEntries = configured.stream()
                .filter(entry -> {
                    if (!entry.getEnabled()) {
                        diagnostics.add(diagnostic(entry, PassiveMarketUniverseDiagnostic.Code.DISABLED_ENTRY,
                                PassiveMarketUniverseDiagnostic.Severity.INFO, "Entry is disabled"));
                        return false;
                    }
                    return true;
                })
                .toList();
        if (enabledEntries.isEmpty()) {
            return universe(configurationVersion, resolvedAt, List.of(), diagnostics);
        }

        List<MarketResponse> catalogue;
        try {
            catalogue = marketDataClient.findAllMarkets();
        } catch (RuntimeException exception) {
            diagnostics.add(new PassiveMarketUniverseDiagnostic(
                    PassiveMarketUniverseDiagnostic.Code.CATALOGUE_UNAVAILABLE,
                    PassiveMarketUniverseDiagnostic.Severity.ERROR,
                    null, null, null, "Market catalogue is unavailable"));
            return universe(configurationVersion, resolvedAt, List.of(), diagnostics);
        }
        if (catalogue == null) {
            diagnostics.add(new PassiveMarketUniverseDiagnostic(
                    PassiveMarketUniverseDiagnostic.Code.CATALOGUE_UNAVAILABLE,
                    PassiveMarketUniverseDiagnostic.Severity.ERROR,
                    null, null, null, "Market catalogue is unavailable"));
            return universe(configurationVersion, resolvedAt, List.of(), diagnostics);
        }

        Map<UUID, List<MarketResponse>> catalogueById = catalogue.stream()
                .filter(market -> market != null && market.marketId() != null)
                .collect(Collectors.groupingBy(MarketResponse::marketId));
        List<PassiveMarketEntry> resolved = new ArrayList<>();
        for (Entry entry : enabledEntries) {
            List<MarketResponse> matches = catalogueById.getOrDefault(entry.getMarketId(), List.of());
            if (matches.isEmpty()) {
                diagnostics.add(diagnostic(entry, PassiveMarketUniverseDiagnostic.Code.UNKNOWN_MARKET,
                        PassiveMarketUniverseDiagnostic.Severity.WARNING, "Canonical market is not in the catalogue"));
                continue;
            }
            if (matches.size() > 1) {
                diagnostics.add(diagnostic(entry,
                        PassiveMarketUniverseDiagnostic.Code.CATALOGUE_IDENTITY_CONFLICT,
                        PassiveMarketUniverseDiagnostic.Severity.ERROR,
                        "Catalogue contains conflicting identities for the canonical market"));
                continue;
            }
            MarketResponse market = matches.get(0);
            if (!entry.getProvider().equals(market.provider())) {
                diagnostics.add(diagnostic(entry, PassiveMarketUniverseDiagnostic.Code.PROVIDER_MISMATCH,
                        PassiveMarketUniverseDiagnostic.Severity.ERROR, "Configured provider does not match the catalogue"));
                continue;
            }
            if (!entry.getProductType().equals(market.productType())) {
                diagnostics.add(diagnostic(entry, PassiveMarketUniverseDiagnostic.Code.PRODUCT_TYPE_MISMATCH,
                        PassiveMarketUniverseDiagnostic.Severity.ERROR,
                        "Configured product type does not match the catalogue"));
                continue;
            }
            resolved.add(new PassiveMarketEntry(entry.getMarketId(), entry.getProvider(), entry.getProductType(),
                    entry.getCadence(), entry.getMinimumEvidenceProfile(), market.marketState()));
        }
        resolved.sort(ENTRY_ORDER);
        return universe(configurationVersion, resolvedAt, resolved, diagnostics);
    }

    private void validateDuplicateConfigurationEntries(List<Entry> entries) {
        Set<String> identities = new HashSet<>();
        for (Entry entry : entries) {
            String identity = entry.getMarketId() + "|" + entry.getProvider() + "|" + entry.getProductType();
            if (!identities.add(identity)) {
                throw new IllegalStateException("Duplicate passive market universe identity: " + identity);
            }
        }
    }

    private PassiveMarketUniverse universe(
            String configurationVersion,
            Instant resolvedAt,
            List<PassiveMarketEntry> entries,
            List<PassiveMarketUniverseDiagnostic> diagnostics
    ) {
        String fingerprint = fingerprint(configurationVersion, entries);
        return new PassiveMarketUniverse(configurationVersion,
                FINGERPRINT_ALGORITHM, fingerprint, resolvedAt, entries, diagnostics);
    }

    private PassiveMarketUniverseDiagnostic diagnostic(
            Entry entry,
            PassiveMarketUniverseDiagnostic.Code code,
            PassiveMarketUniverseDiagnostic.Severity severity,
            String message
    ) {
        return new PassiveMarketUniverseDiagnostic(code, severity, entry.getMarketId(), entry.getProvider(),
                entry.getProductType(), message);
    }

    private String fingerprint(String configurationVersion, List<PassiveMarketEntry> entries) {
        List<FingerprintEntry> payload = entries.stream()
                .sorted(ENTRY_ORDER)
                .map(entry -> new FingerprintEntry(entry.marketId().toString(), entry.provider(),
                        entry.productType(), entry.cadence().toString(), entry.minimumEvidenceProfile().name()))
                .toList();
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(
                    new FingerprintPayload(configurationVersion, payload));
            return sha256Hex(serialized);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize passive market universe fingerprint payload", exception);
        }
    }

    private String sha256Hex(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record FingerprintPayload(String version, List<FingerprintEntry> entries) {
        private FingerprintPayload {
            entries = List.copyOf(entries);
        }
    }

    private record FingerprintEntry(
            String marketId,
            String provider,
            String productType,
            String cadence,
            String minimumEvidenceProfile
    ) {
    }
}
