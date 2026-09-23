package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextAssessmentInput;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextRole;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextRoleDefinition;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrendContextInputMapperTest {
    private static final UUID MARKET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant ASSESSMENT_AT = Instant.parse("2026-09-23T12:00:00Z");
    private static final Instant CUTOFF_AT = Instant.parse("2026-09-23T11:00:00Z");
    private final TrendContextInputMapper mapper = new TrendContextInputMapper();

    @Test
    void preservesProvenanceAndExcludesSyntheticAndOpenCandles() {
        Instant biasOpen = Instant.parse("2026-09-23T04:00:00Z");
        Instant setupOpen = Instant.parse("2026-09-23T10:00:00Z");
        Instant fetchedAt = Instant.parse("2026-09-23T11:05:00Z");

        OhlcResponse bias = response("4H", biasOpen, biasOpen.plus(Duration.ofHours(4)), true,
                false, "kraken:real-bias", fetchedAt);
        OhlcResponse setup = response("1H", setupOpen, setupOpen.plus(Duration.ofHours(1)), true,
                false, "kraken:real-setup", fetchedAt);
        OhlcResponse synthetic = response("1H", setupOpen.minus(Duration.ofHours(1)),
                setupOpen, true, true, "normalizer:gap", fetchedAt);

        TrendContextAssessmentInput input = mapper.map(
                Map.of(
                        TrendContextRole.BIAS, List.of(bias),
                        TrendContextRole.SETUP, List.of(setup, synthetic)),
                profile(), ASSESSMENT_AT, CUTOFF_AT, "trend-context-rules-1");

        assertThat(input.roleSeries().get(TrendContextRole.SETUP).candles())
                .extracting(value -> value.sourceId())
                .containsExactly("normalizer:gap", "kraken:real-setup");
        assertThat(input.roleSeries().get(TrendContextRole.SETUP).calculationReadyCandles())
                .extracting(value -> value.sourceId())
                .containsExactly("kraken:real-setup");
        assertThat(input.roleSeries().get(TrendContextRole.SETUP).gapFindings())
                .extracting(value -> value.code())
                .containsExactly("SYNTHETIC_DATA_EXCLUDED");
        assertThat(input.roleSeries().get(TrendContextRole.SETUP).sourceReference().marketId())
                .isEqualTo(MARKET_ID);
        assertThat(input.roleSeries().get(TrendContextRole.SETUP).candles().getFirst().fetchedAt())
                .isEqualTo(fetchedAt);
        assertThat(input.fingerprint()).isNotBlank();
    }

    @Test
    void collapsesIdenticalDuplicateAndRejectsConflictingDuplicate() {
        OhlcResponse bias = response("4H", Instant.parse("2026-09-23T04:00:00Z"),
                Instant.parse("2026-09-23T08:00:00Z"), true, false, "bias", Instant.parse("2026-09-23T11:05:00Z"));
        OhlcResponse setup = response("1H", Instant.parse("2026-09-23T10:00:00Z"),
                Instant.parse("2026-09-23T11:00:00Z"), true, false, "setup", Instant.parse("2026-09-23T11:05:00Z"));

        TrendContextAssessmentInput input = mapper.map(
                Map.of(TrendContextRole.BIAS, List.of(bias),
                        TrendContextRole.SETUP, List.of(setup, setup)),
                profile(), ASSESSMENT_AT, CUTOFF_AT, "trend-context-rules-1");
        assertThat(input.roleSeries().get(TrendContextRole.SETUP).candles()).hasSize(1);

        OhlcResponse conflicting = new OhlcResponse(
                setup.marketId(), setup.provider(), setup.symbol(), setup.interval(), setup.openTime(),
                setup.closeTime(), setup.open(), new BigDecimal("106"), setup.low(),
                new BigDecimal("103"), setup.volume(), setup.vwap(), setup.trades(), setup.closed(),
                setup.occurredAt(), setup.synthetic(), setup.sourceId(), setup.fetchedAt());
        assertThatThrownBy(() -> mapper.map(
                Map.of(TrendContextRole.BIAS, List.of(bias),
                        TrendContextRole.SETUP, List.of(setup, conflicting)),
                profile(), ASSESSMENT_AT, CUTOFF_AT, "trend-context-rules-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conflicting OHLC duplicate");
    }

    @Test
    void rejectsMarketIdentityConflictAcrossRoles() {
        OhlcResponse bias = response("4H", Instant.parse("2026-09-23T04:00:00Z"),
                Instant.parse("2026-09-23T08:00:00Z"), true, false, "bias", Instant.parse("2026-09-23T11:05:00Z"));
        OhlcResponse setup = response("1H", Instant.parse("2026-09-23T10:00:00Z"),
                Instant.parse("2026-09-23T11:00:00Z"), true, false, "setup", Instant.parse("2026-09-23T11:05:00Z"));
        OhlcResponse differentProvider = new OhlcResponse(
                setup.marketId(), "OTHER", setup.symbol(), setup.interval(), setup.openTime(),
                setup.closeTime(), setup.open(), setup.high(), setup.low(), setup.close(),
                setup.volume(), setup.vwap(), setup.trades(), setup.closed(), setup.occurredAt(),
                setup.synthetic(), setup.sourceId(), setup.fetchedAt());

        assertThatThrownBy(() -> mapper.map(
                Map.of(TrendContextRole.BIAS, List.of(bias),
                        TrendContextRole.SETUP, List.of(differentProvider)),
                profile(), ASSESSMENT_AT, CUTOFF_AT, "trend-context-rules-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Market source identity mismatch");
    }

    private TrendContextProfile profile() {
        EnumMap<TrendContextRole, TrendContextRoleDefinition> roles = new EnumMap<>(TrendContextRole.class);
        roles.put(TrendContextRole.BIAS, new TrendContextRoleDefinition(
                TrendContextRole.BIAS, "4H", Duration.ofHours(4), true, 1, 1));
        roles.put(TrendContextRole.SETUP, new TrendContextRoleDefinition(
                TrendContextRole.SETUP, "1H", Duration.ofHours(1), true, 1, 3));
        return TrendContextProfile.conservativeSwingV1(roles);
    }

    private OhlcResponse response(
            String interval,
            Instant openTime,
            Instant closeTime,
            boolean closed,
            boolean synthetic,
            String sourceId,
            Instant fetchedAt
    ) {
        return new OhlcResponse(
                MARKET_ID, "KRAKEN", "BTC/EUR", interval, openTime, closeTime,
                new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("95"),
                new BigDecimal("102"), new BigDecimal("10"), new BigDecimal("101"),
                2, closed, closeTime, synthetic, sourceId, fetchedAt);
    }
}
