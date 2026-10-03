package com.hope.trading.market_data.service;

import com.hope.trading.market_data.brokerClient.MarketDataProvider;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.kraken.brokerClient.KrakenHttpClient;
import com.hope.trading.market_data.kraken.brokerClient.KrakenMarketData;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcResponse;
import com.hope.trading.market_data.kraken.helper.KrakenMarketMapper;
import com.hope.trading.market_data.kraken.helper.KrakenProviderSymbolResolver;
import com.hope.trading.market_data.kraken.helper.KrakenRestOhlcMapper;
import com.hope.trading.market_data.kraken.helper.KrakenRestTickerMapper;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketFactsRequest;
import com.hope.trading.market_data.model.MarketFactStatus;
import com.hope.trading.market_data.model.MarketFactsResponse;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.repository.MarketRepository;
import com.hope.trading.market_data.service.MarketFactsService;
import com.hope.trading.market_data.service.MarketHistoryService;
import com.hope.trading.market_data.service.OhlcHistoryNormalizer;
import com.hope.trading.market_data.service.MarketService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketFactsKrakenIntegrationTest {
    private static final UUID MARKET_ID = UUID.fromString("6c5e1dcf-5d50-4bcb-92e4-4a55bd7e5f01");
    private static final Instant BOUNDARY = Instant.parse("2026-10-03T12:00:17Z");

    @Test
    void providerShapedKrakenHistoryWithCurrentOpenCandleIsAvailable() throws Exception {
        KrakenHttpClient client = mock(KrakenHttpClient.class);
        MarketRepository repository = mock(MarketRepository.class);
        MarketService marketService = mock(MarketService.class);
        Market market = Market.builder()
                .marketId(MARKET_ID)
                .provider(MarketProvider.KRAKEN)
                .symbol("XBT/EUR")
                .baseAsset("XBT")
                .quoteAsset("EUR")
                .build();
        when(repository.findById(MARKET_ID)).thenReturn(Optional.of(market));
        when(marketService.findById(MARKET_ID)).thenReturn(Optional.of(market));
        when(client.findOhlcHistory("XBTEUR", 1)).thenReturn(responseWithCompletedAndOpenCandle());

        Clock clock = Clock.fixed(BOUNDARY, ZoneOffset.UTC);
        KrakenMarketData provider = new KrakenMarketData(
                client,
                mock(KrakenMarketMapper.class),
                new KrakenProviderSymbolResolver(),
                new KrakenRestOhlcMapper(),
                mock(KrakenRestTickerMapper.class),
                new OhlcHistoryNormalizer(),
                clock);
        MarketHistoryService historyService = new MarketHistoryService(
                repository, List.<MarketDataProvider>of(provider));
        MarketFactsService factsService = new MarketFactsService(
                marketService, historyService, clock);

        MarketFactsResponse response = factsService.find(new MarketFactsRequest(
                MARKET_ID, OhlcInterval.ONE_MINUTE, Duration.ofMinutes(15),
                15, 1, Duration.ofMinutes(5)));

        assertThat(response.readiness().expectedCompletedCandleCount()).isEqualTo(14);
        assertThat(response.readiness().observedCompletedCandles()).isEqualTo(14);
        assertThat(response.readiness().missingIntervalCount()).isZero();
        assertThat(response.readiness().cadenceViolationCount()).isZero();
        assertThat(response.readiness().status()).isEqualTo(MarketFactStatus.AVAILABLE);
    }

    private KrakenOhlcResponse responseWithCompletedAndOpenCandle() throws Exception {
        ObjectMapper json = new ObjectMapper();
        ObjectNode result = json.createObjectNode();
        result.put("last", BOUNDARY.getEpochSecond());
        ArrayNode entries = result.putArray("XBTEUR");
        for (int minute = 46; minute <= 60; minute++) {
            String timestamp = minute == 60
                    ? "2026-10-03T12:00:00Z"
                    : "2026-10-03T11:" + minute + ":00Z";
            long epoch = Instant.parse(timestamp).getEpochSecond();
            entries.addArray().add(Long.toString(epoch))
                    .add("100").add("101").add("99").add("100")
                    .add("100").add("1").add(10);
        }
        return new KrakenOhlcResponse(List.of(), result);
    }
}
