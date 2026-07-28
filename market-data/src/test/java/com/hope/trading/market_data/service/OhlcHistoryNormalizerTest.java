package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OhlcHistoryNormalizerTest {

    private static final UUID MARKET_ID =
            UUID.fromString("e7196fd5-88f0-4fb7-aeb2-fae87bd4ce70");
    private final OhlcHistoryNormalizer normalizer =
            new OhlcHistoryNormalizer();

    @Test
    void returnsEmptyListForMissingHistory() {
        assertThat(normalizer.fillMissingIntervals(
                null,
                OhlcInterval.ONE_MINUTE
        )).isEmpty();
        assertThat(normalizer.fillMissingIntervals(
                List.of(),
                OhlcInterval.ONE_MINUTE
        )).isEmpty();
    }

    @Test
    void sortsCandlesAndFillsEveryMissingInterval() {
        OhlcEvent first = event(
                "2026-07-28T10:00:00Z",
                "100.00",
                true
        );
        OhlcEvent last = event(
                "2026-07-28T10:03:00Z",
                "103.00",
                false
        );

        List<OhlcEvent> normalized = normalizer.fillMissingIntervals(
                List.of(last, first),
                OhlcInterval.ONE_MINUTE
        );

        assertThat(normalized)
                .extracting(OhlcEvent::openTime)
                .containsExactly(
                        Instant.parse("2026-07-28T10:00:00Z"),
                        Instant.parse("2026-07-28T10:01:00Z"),
                        Instant.parse("2026-07-28T10:02:00Z"),
                        Instant.parse("2026-07-28T10:03:00Z")
                );

        assertThat(normalized.subList(1, 3))
                .allSatisfy(synthetic -> {
                    assertThat(synthetic.open()).isEqualByComparingTo("100.00");
                    assertThat(synthetic.high()).isEqualByComparingTo("100.00");
                    assertThat(synthetic.low()).isEqualByComparingTo("100.00");
                    assertThat(synthetic.close()).isEqualByComparingTo("100.00");
                    assertThat(synthetic.volume()).isEqualByComparingTo("0");
                    assertThat(synthetic.trades()).isZero();
                    assertThat(synthetic.closed()).isTrue();
                });
        assertThat(normalized.getLast()).isSameAs(last);
    }

    private OhlcEvent event(
            String openTimeValue,
            String closeValue,
            boolean closed
    ) {
        Instant openTime = Instant.parse(openTimeValue);
        BigDecimal close = new BigDecimal(closeValue);

        return new OhlcEvent(
                MARKET_ID,
                MarketProvider.KRAKEN,
                "XBT/EUR",
                OhlcInterval.ONE_MINUTE,
                openTime,
                openTime.plusSeconds(60),
                close,
                close,
                close,
                close,
                BigDecimal.TEN,
                close,
                4,
                closed,
                openTime
        );
    }
}
