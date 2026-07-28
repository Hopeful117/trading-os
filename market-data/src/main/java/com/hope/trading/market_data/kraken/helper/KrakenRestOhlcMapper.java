package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.kraken.dto.ohlc.KrakenRestOhlcEntry;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcResponse;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcResult;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.StreamSupport;

@Component
public class KrakenRestOhlcMapper {
    public KrakenOhlcResult extract(
            KrakenOhlcResponse response
    ) {
        validateResponse(response);

        JsonNode result = response.result();
        JsonNode lastNode = result.get("last");

        if (lastNode == null || !lastNode.canConvertToLong()) {
            throw new IllegalArgumentException(
                    "Kraken OHLC response contains an invalid last timestamp"
            );
        }

        String providerSymbol =
                findProviderSymbol(result);

        JsonNode entriesNode =
                result.get(providerSymbol);

        if (entriesNode == null || !entriesNode.isArray()) {
            throw new IllegalArgumentException(
                    "Kraken OHLC entries are missing for symbol "
                            + providerSymbol
            );
        }

        List<KrakenRestOhlcEntry> entries =
                StreamSupport.stream(
                                entriesNode.spliterator(),
                                false
                        )
                        .map(this::mapEntry)
                        .toList();

        return new KrakenOhlcResult(
                providerSymbol,
                entries,
                Instant.ofEpochSecond(
                        lastNode.asLong()
                )
        );
    }

    private void validateResponse(
            KrakenOhlcResponse response
    ) {
        if (response == null) {
            throw new IllegalArgumentException(
                    "Kraken OHLC response is required"
            );
        }

        if (response.error() != null
                && !response.error().isEmpty()) {
            throw new IllegalStateException(
                    "Kraken OHLC request failed: "
                            + String.join(", ", response.error())
            );
        }

        if (response.result() == null
                || !response.result().isObject()) {
            throw new IllegalArgumentException(
                    "Kraken OHLC response result is missing"
            );
        }
    }

    private String findProviderSymbol(
            JsonNode result
    ) {
        return result.propertyNames()
                .stream()
                .filter(property ->
                        !"last".equals(property)
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Kraken OHLC response does not contain a market symbol"
                        )
                );
    }

    private KrakenRestOhlcEntry mapEntry(
            JsonNode node
    ) {
        if (!node.isArray() || node.size() < 8) {
            throw new IllegalArgumentException(
                    "Invalid Kraken OHLC entry: " + node
            );
        }

        return new KrakenRestOhlcEntry(
                Instant.ofEpochSecond(
                        node.get(0).asLong()
                ),
                decimal(node, 1),
                decimal(node, 2),
                decimal(node, 3),
                decimal(node, 4),
                decimal(node, 5),
                decimal(node, 6),
                node.get(7).asInt()
        );
    }

    private BigDecimal decimal(
            JsonNode node,
            int index
    ) {
        JsonNode value = node.get(index);

        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(
                    "Missing Kraken OHLC value at index "
                            + index
            );
        }

        return new BigDecimal(
                value.asString()
        );
    }
    public OhlcEvent toEvent(
            KrakenRestOhlcEntry entry,
            Market market,
            OhlcInterval interval,
            boolean closed,
            Instant occurredAt
    ) {
        Instant closeTime =
                entry.openTime()
                        .plus(interval.getDuration());

        return new OhlcEvent(
                market.getMarketId(),
                market.getProvider(),
                market.getSymbol(),
                interval,
                entry.openTime(),
                closeTime,
                entry.open(),
                entry.high(),
                entry.low(),
                entry.close(),
                entry.volume(),
                entry.vwap(),
                entry.trades(),
                closed,
                occurredAt
        );
    }
}
