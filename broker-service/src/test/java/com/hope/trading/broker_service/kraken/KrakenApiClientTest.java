package com.hope.trading.broker_service.kraken;

import com.hope.trading.broker_service.dto.*;
import com.hope.trading.broker_service.kraken.httpClient.KrakenHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KrakenApiClientTest {
    @Mock private KrakenHttpClient httpClient;
    @Mock private KrakenMapper krakenMapper;
    @InjectMocks private KrakenApiClient apiClient;

    @Test
    void getBrokerNameReturnsKraken() {
        assertThat(apiClient.getBrokerName()).isEqualTo("KRAKEN");
    }

    @Test
    void getBaseCurrencyReturnsEur() {
        assertThat(apiClient.getBaseCurrency()).isEqualTo("EUR");
    }

    @Test
    void getBalanceDelegatesToHttpClientThenMapper() {
        KrakenAccountBalanceResponse response = KrakenAccountBalanceResponse.builder()
                .result(Map.of("EUR", new BigDecimal("1000")))
                .build();
        AccountBalance expected = AccountBalance.builder()
                .balances(response.getResult())
                .build();

        when(httpClient.getBalances()).thenReturn(response);
        when(krakenMapper.toAccountBalance(response)).thenReturn(expected);

        AccountBalance result = apiClient.getBalance();

        assertThat(result).isEqualTo(expected);
        verify(httpClient).getBalances();
        verify(krakenMapper).toAccountBalance(response);
    }

    @Test
    void getMarketPriceDelegatesToHttpClientThenMapper() {
        KrakenTickerResponse response = KrakenTickerResponse.builder().build();
        MarketPrice expected = MarketPrice.builder()
                .symbol("BTCUSD")
                .price(new BigDecimal("50000"))
                .build();

        when(httpClient.getTicker("BTCUSD")).thenReturn(response);
        when(krakenMapper.toMarketPrice(response, "BTCUSD")).thenReturn(expected);

        MarketPrice result = apiClient.getMarketPrice("BTCUSD");

        assertThat(result).isEqualTo(expected);
        verify(httpClient).getTicker("BTCUSD");
        verify(krakenMapper).toMarketPrice(response, "BTCUSD");
    }

    @Test
    void getOpenPositionsWithNullResultsReturnsEmptyList() {
        KrakenOpenPositionResponse response = KrakenOpenPositionResponse.builder()
                .results(null)
                .build();

        when(httpClient.getOpenPositions()).thenReturn(response);

        List<Position> result = apiClient.getOpenPositions();

        assertThat(result).isEmpty();
        verify(krakenMapper, never()).toPosition(any(), any());
    }

    @Test
    void getOpenPositionsWithEmptyResultsReturnsEmptyList() {
        KrakenOpenPositionResponse response = KrakenOpenPositionResponse.builder()
                .results(new LinkedHashMap<>())
                .build();

        when(httpClient.getOpenPositions()).thenReturn(response);

        List<Position> result = apiClient.getOpenPositions();

        assertThat(result).isEmpty();
        verify(krakenMapper, never()).toPosition(any(), any());
    }

    @Test
    void getOpenPositionsWithResultsMapsEachEntry() {
        KrakenOpenPositionResult pos1 = KrakenOpenPositionResult.builder()
                .pair("XXBTZUSD").type("buy").vol(BigDecimal.ONE).build();
        KrakenOpenPositionResult pos2 = KrakenOpenPositionResult.builder()
                .pair("XETHZEUR").type("sell").vol(BigDecimal.TEN).build();

        Map<String, KrakenOpenPositionResult> results = new LinkedHashMap<>();
        results.put("id1", pos1);
        results.put("id2", pos2);

        KrakenOpenPositionResponse response = KrakenOpenPositionResponse.builder()
                .results(results)
                .build();

        Position mapped1 = Position.builder().brokerPositionId("id1").symbol("BTCUSD").build();
        Position mapped2 = Position.builder().brokerPositionId("id2").symbol("ETHEUR").build();

        when(httpClient.getOpenPositions()).thenReturn(response);
        when(krakenMapper.toPosition("id1", pos1)).thenReturn(mapped1);
        when(krakenMapper.toPosition("id2", pos2)).thenReturn(mapped2);

        List<Position> result = apiClient.getOpenPositions();

        assertThat(result).containsExactly(mapped1, mapped2);
        verify(krakenMapper).toPosition("id1", pos1);
        verify(krakenMapper).toPosition("id2", pos2);
    }
}
