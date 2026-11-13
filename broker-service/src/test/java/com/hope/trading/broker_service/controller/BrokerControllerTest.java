package com.hope.trading.broker_service.controller;

import com.hope.trading.broker_service.dto.AccountBalance;
import com.hope.trading.broker_service.dto.BrokerAccountDto;
import com.hope.trading.broker_service.dto.MarketPrice;
import com.hope.trading.broker_service.dto.Position;
import com.hope.trading.broker_service.service.BrokerService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BrokerControllerTest {
    private final BrokerService service = mock(BrokerService.class);
    private final BrokerController controller = new BrokerController(service);

    @Test
    void delegatesLegacyBrokerQueries() {
        AccountBalance balance = new AccountBalance();
        List<Position> positions = List.of();
        MarketPrice ticker = mock(MarketPrice.class);
        BrokerAccountDto account = mock(BrokerAccountDto.class);
        when(service.getBalance()).thenReturn(balance);
        when(service.getOpenPositions()).thenReturn(positions);
        when(service.getMarketPrice("EURUSD")).thenReturn(ticker);
        when(service.getAccount()).thenReturn(account);

        assertThat(controller.getBalance().getBody()).isSameAs(balance);
        assertThat(controller.getPositions().getBody()).isSameAs(positions);
        assertThat(controller.getTicker("EURUSD").getBody()).isSameAs(ticker);
        assertThat(controller.getAccount().getBody()).isSameAs(account);

        verify(service).getBalance();
        verify(service).getOpenPositions();
        verify(service).getMarketPrice("EURUSD");
        verify(service).getAccount();
    }
}
