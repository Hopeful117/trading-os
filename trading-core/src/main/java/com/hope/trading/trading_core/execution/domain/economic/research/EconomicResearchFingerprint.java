package com.hope.trading.trading_core.execution.domain.economic.research;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

final class EconomicResearchFingerprint {
    private EconomicResearchFingerprint() {
    }

    static String of(String... values) {
        StringBuilder material = new StringBuilder();
        for (String value : values) {
            String normalized = Objects.toString(value, "");
            material.append(normalized.length()).append(':').append(normalized);
        }
        return sha256(material.toString());
    }

    static String of(List<String> values) {
        return of(values.toArray(String[]::new));
    }

    static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
