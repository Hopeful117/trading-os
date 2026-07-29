package com.hope.trading.market_intelligence.domain.artifact;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public record ArtifactFingerprint(String value) {
    public ArtifactFingerprint {
        value = Objects.requireNonNull(value, "fingerprint");
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Fingerprint must be a SHA-256 hexadecimal value");
        }
    }

    public static ArtifactFingerprint ofParameters(Map<String, ?> parameters) {
        return hash(parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> escape(entry.getKey()) + "=" + canonical(entry.getValue()))
                .toList());
    }

    public static ArtifactFingerprint ofInputs(Collection<String> inputIdentities) {
        return hash(inputIdentities.stream().sorted().toList());
    }

    public static ArtifactFingerprint empty() {
        return hash(List.of());
    }

    private static ArtifactFingerprint hash(List<String> components) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    String.join("\u001f", components).getBytes(StandardCharsets.UTF_8)
            );
            return new ArtifactFingerprint(HexFormat.of().formatHex(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("=", "\\=")
                .replace("\u001f", "\\u001f");
    }

    private static String canonical(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> Map.entry(
                            String.valueOf(entry.getKey()), entry.getValue()
                    ))
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> escape(entry.getKey()) + ":" + canonical(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Set<?> set) {
            return set.stream()
                    .map(ArtifactFingerprint::canonical)
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(ArtifactFingerprint::canonical)
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<String> values = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                values.add(canonical(java.lang.reflect.Array.get(value, index)));
            }
            return String.join(",", values);
        }
        return escape(String.valueOf(value));
    }
}
