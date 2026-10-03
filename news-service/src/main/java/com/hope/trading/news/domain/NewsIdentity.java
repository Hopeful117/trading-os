package com.hope.trading.news.domain;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class NewsIdentity {
    private static final UUID NAMESPACE = UUID.fromString("b3c1f8b1-0f6d-4a2d-a85c-06d6a3f9e731");

    private NewsIdentity() {
    }

    public static UUID eventId(String sourceName, String sourceEventId) {
        return UUID.nameUUIDFromBytes((NAMESPACE + ":event:" + sourceName + ":" + sourceEventId)
                .getBytes(StandardCharsets.UTF_8));
    }

    public static UUID newsItemId(String sourceName, String sourceItemId) {
        return UUID.nameUUIDFromBytes((NAMESPACE + ":item:" + sourceName + ":" + sourceItemId)
                .getBytes(StandardCharsets.UTF_8));
    }
}
