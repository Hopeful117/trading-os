package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class OhlcHistoryNormalizer {
    public List<OhlcEvent> fillMissingIntervals(
            List<OhlcEvent> events,
            OhlcInterval interval
    ) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        List<OhlcEvent> sortedEvents = events.stream()
                .sorted(
                        Comparator.comparing(
                                OhlcEvent::openTime
                        ).thenComparing(OhlcEvent::closeTime)
                                .thenComparing(OhlcEvent::sourceId)
                )
                .toList();

        List<OhlcEvent> deduplicatedEvents = new ArrayList<>();
        for (OhlcEvent event : sortedEvents) {
            if (!event.interval().equals(interval)) {
                throw new IllegalArgumentException(
                        "OHLC event interval does not match requested interval");
            }

            OhlcEvent duplicate = deduplicatedEvents.stream()
                    .filter(existing -> existing.openTime().equals(event.openTime()))
                    .findFirst()
                    .orElse(null);
            if (duplicate == null) {
                deduplicatedEvents.add(event);
                continue;
            }
            if (!sameCandleContent(duplicate, event)) {
                throw new IllegalArgumentException(
                        "Conflicting OHLC duplicate at " + event.openTime());
            }
        }

        List<OhlcEvent> normalizedEvents =
                new ArrayList<>();

        OhlcEvent previous =
                deduplicatedEvents.getFirst();

        normalizedEvents.add(previous);

        for (
                int index = 1;
                        index < deduplicatedEvents.size();
                        index++
                ) {
            OhlcEvent current =
                    deduplicatedEvents.get(index);

            Instant expectedOpenTime =
                    previous.openTime()
                            .plus(interval.getDuration());

            while (
                    expectedOpenTime.isBefore(
                            current.openTime()
                    )
            ) {
                OhlcEvent syntheticEvent =
                        createSyntheticEvent(
                                previous,
                                expectedOpenTime,
                                interval
                        );

                normalizedEvents.add(
                        syntheticEvent
                );

                previous = syntheticEvent;

                expectedOpenTime =
                        previous.openTime()
                                .plus(
                                        interval.getDuration()
                                );
            }

            normalizedEvents.add(current);
                previous = current;
        }

        return List.copyOf(normalizedEvents);
    }

    private boolean sameCandleContent(OhlcEvent first, OhlcEvent second) {
        return Objects.equals(first.marketId(), second.marketId())
                && first.provider() == second.provider()
                && Objects.equals(first.symbol(), second.symbol())
                && first.interval() == second.interval()
                && Objects.equals(first.openTime(), second.openTime())
                && Objects.equals(first.closeTime(), second.closeTime())
                && Objects.equals(first.open(), second.open())
                && Objects.equals(first.high(), second.high())
                && Objects.equals(first.low(), second.low())
                && Objects.equals(first.close(), second.close())
                && Objects.equals(first.volume(), second.volume())
                && Objects.equals(first.vwap(), second.vwap())
                && Objects.equals(first.trades(), second.trades())
                && first.closed() == second.closed()
                && Objects.equals(first.occurredAt(), second.occurredAt())
                && first.synthetic() == second.synthetic();
    }

    private OhlcEvent createSyntheticEvent(
            OhlcEvent previous,
            Instant openTime,
            OhlcInterval interval
    ) {
        BigDecimal previousClose =
                previous.close();

        return new OhlcEvent(
                previous.marketId(),
                previous.provider(),
                previous.symbol(),
                interval,
                openTime,
                openTime.plus(
                        interval.getDuration()
                ),
                previousClose,
                previousClose,
                previousClose,
                previousClose,
                BigDecimal.ZERO,
                previousClose,
                0,
                true,
                previous.occurredAt(),
                true,
                OhlcEvent.defaultSourceId(
                        previous.marketId(),
                        previous.provider(),
                        previous.symbol(),
                        interval,
                        openTime
                ),
                previous.fetchedAt()
        );
    }
}
