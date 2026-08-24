package com.hope.trading.broker_service.service;

import com.hope.trading.broker_service.brokerClient.BrokerProvider;
import com.hope.trading.broker_service.dto.AccountBalance;
import com.hope.trading.broker_service.dto.BrokerAccountDto;
import com.hope.trading.broker_service.dto.MarketPrice;
import com.hope.trading.broker_service.dto.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrokerServiceImplTest {
    @Mock private BrokerProvider brokerProvider;
    @InjectMocks private BrokerServiceImpl brokerService;

    @Test
    void getBalanceDelegatesToProvider() {
        AccountBalance expected = AccountBalance.builder()
                .balances(java.util.Map.of("EUR", new BigDecimal("5000")))
                .build();
        when(brokerProvider.getBalance()).thenReturn(expected);

        AccountBalance result = brokerService.getBalance();

        assertThat(result).isEqualTo(expected);
        verify(brokerProvider).getBalance();
    }

    @Test
    void getOpenPositionsDelegatesToProvider() {
        List<Position> expected = List.of(
                Position.builder().brokerPositionId("p1").build(),
                Position.builder().brokerPositionId("p2").build()
        );
        when(brokerProvider.getOpenPositions()).thenReturn(expected);

        List<Position> result = brokerService.getOpenPositions();

        assertThat(result).isEqualTo(expected);
        verify(brokerProvider).getOpenPositions();
    }

    @Test
    void getMarketPriceDelegatesToProvider() {
        MarketPrice expected = MarketPrice.builder()
                .symbol("BTCUSD")
                .price(new BigDecimal("50000"))
                .build();
        when(brokerProvider.getMarketPrice("BTCUSD")).thenReturn(expected);

        MarketPrice result = brokerService.getMarketPrice("BTCUSD");

        assertThat(result).isEqualTo(expected);
        verify(brokerProvider).getMarketPrice("BTCUSD");
    }

    @Test
    void getAccountBuildsBrokerAccountDtoCorrectly() {
        when(brokerProvider.getBrokerName()).thenReturn("KRAKEN");
        when(brokerProvider.getBaseCurrency()).thenReturn("EUR");

        AccountBalance balance = AccountBalance.builder()
                .balances(java.util.Map.of("EUR", new BigDecimal("1000")))
                .build();
        List<Position> positions = List.of(
                Position.builder().brokerPositionId("p1").build()
        );

        when(brokerProvider.getBalance()).thenReturn(balance);
        when(brokerProvider.getOpenPositions()).thenReturn(positions);

        BrokerAccountDto result = brokerService.getAccount();

        assertThat(result.getBroker()).isEqualTo("KRAKEN");
        assertThat(result.getBaseCurrency()).isEqualTo("EUR");
        assertThat(result.getBrokerAccountId()).isEqualTo("kraken-default");
        assertThat(result.getAccountName()).isEqualTo("kraken account");
        assertThat(result.getBalances()).isEqualTo(balance);
        assertThat(result.getOpenTrades()).isEqualTo(positions);
        assertThat(result.getDataAt()).isNotNull();
    }
}
