package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
                        )
                )
                .toList();

        List<OhlcEvent> normalizedEvents =
                new ArrayList<>();

        OhlcEvent previous =
                sortedEvents.getFirst();

        normalizedEvents.add(previous);

        for (
                int index = 1;
                index < sortedEvents.size();
                index++
        ) {
            OhlcEvent current =
                    sortedEvents.get(index);

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
                previous.occurredAt()
        );
    }
}
