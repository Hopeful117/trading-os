package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.ObservationReference;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityDirection;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityOrigin;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Projects a persisted {@link StrategyMatch} into an opportunity creation
 * command (ADR-034). The match is already the setup decision: this factory
 * never re-evaluates, never reads OHLC rules, never infers strategy identity
 * or direction, and never consults risk/broker/ranking/AI.
 *
 * <p>Story 0013 generalized model: scenario and timeframe are derived from
 * declarative {@link StrategyDefinition} metadata, not from strategy-specific
 * branching. The same code path serves all strategies.</p>
 */
@Component
public class StrategyMatchOpportunityFactory {

    /** Stable namespace so one match always seeds one opportunity lineage. */
    private static final UUID OPPORTUNITY_LINEAGE_NAMESPACE =
            UUID.fromString("5f0d1e62-3b7a-4c48-9a2e-6c1d84b90f34");

    public CreateOpportunityCommand command(
            StrategyMatch match,
            StrategyDefinition definition,
            String instrument,
            OpportunityOrigin origin,
            ObservationReference evidence,
            Instant evaluatedAt,
            Instant validFrom,
            Instant validUntil
    ) {
        Objects.requireNonNull(match, "match is required");
        Objects.requireNonNull(definition, "definition is required");
        Objects.requireNonNull(instrument, "instrument is required");
        Objects.requireNonNull(origin, "origin is required");
        Objects.requireNonNull(evidence, "evidence reference is required");
        return new CreateOpportunityCommand(
                instrument,
                directionOf(match),
                definition.scenario(),
                timeframeOf(definition),
                origin,
                Set.of(evidence),
                Set.of(),
                evaluatedAt,
                validUntil,
                match.matchId(),
                deriveOpportunityLineageId(match.matchId()));
    }

    /**
     * Deterministic lineage identity: name-based UUID derivation over the
     * match id (UUIDv3-style). Same match -> same logical opportunity;
     * distinct matches -> distinct lineages; never equal to the matchId.
     */
    public static UUID deriveOpportunityLineageId(UUID matchId) {
        Objects.requireNonNull(matchId, "matchId is required");
        try {
            byte[] name = ("trading-opportunity-lineage:" + matchId)
                    .getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(concat(OPPORTUNITY_LINEAGE_NAMESPACE, name));
            hash[6] = (byte) ((hash[6] & 0x0F) | 0x30);
            hash[8] = (byte) ((hash[8] & 0x3F) | 0x80);
            long msb = 0;
            long lsb = 0;
            for (int i = 0; i < 8; i++) {
                msb = (msb << 8) | (hash[i] & 0xFF);
            }
            for (int i = 8; i < 16; i++) {
                lsb = (lsb << 8) | (hash[i] & 0xFF);
            }
            return new UUID(msb, lsb);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Deterministic opportunity identity failed", exception);
        }
    }

    private static byte[] concat(UUID namespace, byte[] name) {
        byte[] bytes = new byte[16 + name.length];
        long msb = namespace.getMostSignificantBits();
        long lsb = namespace.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (msb >>> (56 - 8 * i));
            bytes[8 + i] = (byte) (lsb >>> (56 - 8 * i));
        }
        System.arraycopy(name, 0, bytes, 16, name.length);
        return bytes;
    }

    private OpportunityDirection directionOf(StrategyMatch match) {
        return switch (match.direction()) {
            case LONG -> OpportunityDirection.LONG;
            case SHORT -> OpportunityDirection.SHORT;
        };
    }

    /**
     * Derives the trader-facing timeframe label from the strategy definition's
     * primary applicability timeframe. Defaults to the first declared timeframe.
     */
    private String timeframeOf(StrategyDefinition definition) {
        return definition.applicability().timeframes().stream()
                .findFirst()
                .map(tf -> tf.name().toLowerCase(Locale.ROOT))
                .orElse("unknown");
    }

    private static final java.util.Locale Locale = java.util.Locale.ROOT;
}
