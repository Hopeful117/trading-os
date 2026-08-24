package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcResponse;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcResult;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * STORY-0020A: protects the Kraken OHLC REST payload contract — provider
 * responses are the raw material of every downstream deterministic analysis,
 * so malformed payloads must fail loudly and valid ones must map losslessly.
 */
class KrakenRestOhlcMapperTest {

    private final KrakenRestOhlcMapper mapper = new KrakenRestOhlcMapper();
    private final ObjectMapper json = new ObjectMapper();

    private ObjectNode validResult() {
        ObjectNode result = json.createObjectNode();
        result.put("last", 1755900000L);
        ArrayNode entries = result.putArray("XBT/EUR");
        // time, open, high, low, close, vwap, volume, trades
        ArrayNode first = entries.addArray();
        first.add("1755899400").add("100").add("110").add("95")
                .add("105").add("102").add("12.5").add(42);
        ArrayNode second = entries.addArray();
        second.add("1755899700").add("105").add("120").add("104")
                .add("118").add("115").add("20.0").add(80);
        return result;
    }

    private KrakenOhlcResponse response(ObjectNode result) {
        return new KrakenOhlcResponse(List.of(), result);
    }

    @Test
    void extractsProviderSymbolEntriesAndLastTimestamp() {
        KrakenOhlcResult extracted = mapper.extract(response(validResult()));

        assertThat(extracted.providerSymbol()).isEqualTo("XBT/EUR");
        assertThat(extracted.last()).isEqualTo(Instant.ofEpochSecond(1755900000));
        assertThat(extracted.entries()).hasSize(2);
        var first = extracted.entries().getFirst();
        assertThat(first.openTime()).isEqualTo(Instant.ofEpochSecond(1755899400));
        assertThat(first.open()).isEqualByComparingTo("100");
        assertThat(first.high()).isEqualByComparingTo("110");
        assertThat(first.low()).isEqualByComparingTo("95");
        assertThat(first.close()).isEqualByComparingTo("105");
        assertThat(first.volume()).isEqualByComparingTo("12.5");
        assertThat(first.trades()).isEqualTo(42);
    }

    @Test
    void providerErrorsFailLoudly() {
        KrakenOhlcResponse failed = new KrakenOhlcResponse(
                List.of("EQuery:Invalid asset pair"), validResult());
        assertThatThrownBy(() -> mapper.extract(failed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EQuery:Invalid asset pair");
    }

    @Test
    void missingEntriesForSymbolFails() {
        ObjectNode result = json.createObjectNode();
        result.put("last", 1755900000L);
        result.put("XBT/EUR", "not-an-array");
        assertThatThrownBy(() -> mapper.extract(response(result)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entries are missing");
    }

    @Test
    void malformedEntryFailsInsteadOfSilentlyTruncating() {
        ObjectNode result = validResult();
        result.set("XBT/EUR", json.createObjectNode()); // entries not an array
        assertThatThrownBy(() -> mapper.extract(response(result)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullPayloadValueFailsExplicitly() {
        ObjectNode result = json.createObjectNode();
        result.put("last", 1L);
        ArrayNode entries = result.putArray("XBT/EUR");
        ArrayNode entry = entries.addArray();
        // Only 7 of the 8 mandatory values: trades count missing.
        entry.add("1755899400").add("100").add("110").add("95")
                .add("105").add("102").add("12.5");
        assertThatThrownBy(() -> mapper.extract(response(result)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Kraken OHLC entry");
    }

    @Test
    void toEventBindsEntryToMarketAndIntervalCloseTime() {
        Market market = new Market();
        market.setMarketId(UUID.randomUUID());
        market.setProvider(com.hope.trading.market_data.helper.MarketProvider.KRAKEN);
        market.setSymbol("BTC/EUR");

        KrakenOhlcResult extracted = mapper.extract(response(validResult()));
        OhlcEvent event = mapper.toEvent(extracted.entries().getFirst(), market,
                OhlcInterval.FIFTEEN_MINUTES, true, Instant.ofEpochSecond(1755900100));

        assertThat(event.marketId()).isEqualTo(market.getMarketId());
        assertThat(event.provider()).isEqualTo(com.hope.trading.market_data.helper.MarketProvider.KRAKEN);
        assertThat(event.symbol()).isEqualTo("BTC/EUR");
        assertThat(event.interval()).isEqualTo(OhlcInterval.FIFTEEN_MINUTES);
        assertThat(event.openTime()).isEqualTo(Instant.ofEpochSecond(1755899400));
        assertThat(event.closeTime())
                .isEqualTo(Instant.ofEpochSecond(1755899400 + 15 * 60));
        assertThat(event.close()).isEqualByComparingTo("105");
        assertThat(event.closed()).isTrue();
        assertThat(event.occurredAt()).isEqualTo(Instant.ofEpochSecond(1755900100));
    }
}
