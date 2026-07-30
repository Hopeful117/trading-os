package com.hope.trading.market_intelligence.domain.opportunity;

import java.time.Instant;
import java.util.*;
import java.util.UUID;

public record UserOpportunity(
        UUID userId, OpportunityId opportunityId, boolean favorite, boolean hidden,
        boolean notificationEnabled, boolean read, Integer customPriority,
        String personalNotes, Instant updatedAt
) {
    public UserOpportunity {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(opportunityId);
        if (customPriority != null && (customPriority < 0 || customPriority > 100)) {
            throw new IllegalArgumentException("Custom priority must be between 0 and 100");
        }
        personalNotes = personalNotes == null ? "" : personalNotes.trim();
        Objects.requireNonNull(updatedAt);
    }
}
