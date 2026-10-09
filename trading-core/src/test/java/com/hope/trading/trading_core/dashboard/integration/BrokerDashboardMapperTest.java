package com.hope.trading.trading_core.dashboard.integration;

import com.hope.trading.trading_core.broker.dto.AccountBalanceDto;
import com.hope.trading.trading_core.broker.dto.BrokerAccountDto;
import com.hope.trading.trading_core.dto.Position;
import com.hope.trading.trading_core.helper.TradeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerDashboardMapperTest {
    private final BrokerDashboardMapper mapper = new BrokerDashboardMapper();

    @Test
    void mapsFeignContractAndDerivesLegacyEntryPrice() {
        BrokerAccountDto dto = BrokerAccountDto.builder()
                .brokerAccountId("kraken-default")
                .broker("KRAKEN")
                .baseCurrency("USD")
                .balances(AccountBalanceDto.builder().balances(Map.of("USD", new BigDecimal("1000"))).build())
                .openTrades(List.of(Position.builder()
                        .brokerPositionId("p1")
                        .symbol("BTC/USD")
                        .side("sell")
                        .quantity(new BigDecimal("2"))
                        .entryValue(new BigDecimal("200"))
                        .build()))
                .build();

        BrokerAccountFact result = mapper.toFact(dto);

        assertThat(result.positions()).hasSize(1);
        assertThat(result.positions().getFirst().side()).isEqualTo(TradeType.SELL);
        assertThat(result.positions().getFirst().entryPrice()).isEqualByComparingTo("100");
        assertThat(result.positionPnlTreatment()).isEqualTo(PositionPnlTreatment.ADDITIVE);
        assertThat(result.balancesAvailable()).isTrue();
        assertThat(result.brokerEquityTotal()).isFalse();
    }

    @Test
    void distinguishesMissingBalancesFromAnEmptyAccount() {
        BrokerAccountFact result = mapper.toFact(BrokerAccountDto.builder()
                .broker("KRAKEN")
                .baseCurrency("USD")
                .build());

        assertThat(result.balances()).isEmpty();
        assertThat(result.balancesAvailable()).isFalse();
    }

    @Test
    void preservesHighPrecisionWhenDerivingEntryPrice() {
        BrokerAccountDto dto = BrokerAccountDto.builder()
                .broker("KRAKEN")
                .baseCurrency("USD")
                .balances(AccountBalanceDto.builder().balances(Map.of()).build())
                .openTrades(List.of(Position.builder()
                        .brokerPositionId("p1")
                        .symbol("BTC/USD")
                        .side("buy")
                        .quantity(new BigDecimal("3"))
                        .entryValue(new BigDecimal("1"))
                        .build()))
                .build();

        BrokerAccountFact result = mapper.toFact(dto);

        assertThat(result.positions().getFirst().entryPrice())
                .isEqualByComparingTo(new BigDecimal("0.3333333333333333333333333333333333"));
    }
}
