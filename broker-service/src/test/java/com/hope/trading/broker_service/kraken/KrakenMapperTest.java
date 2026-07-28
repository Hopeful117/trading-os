package com.hope.trading.broker_service.kraken;

import com.hope.trading.broker_service.dto.KrakenOpenPositionResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class KrakenMapperTest {
    private final KrakenMapper mapper = new KrakenMapper();

    @Test
    void normalizesPositionFactsAndCalculatesEntryPrice() {
        KrakenOpenPositionResult source = KrakenOpenPositionResult.builder()
                .pair("XXBTZUSD")
                .type("buy")
                .vol(new BigDecimal("2"))
                .cost(new BigDecimal("200"))
                .value(new BigDecimal("220"))
                .margin(new BigDecimal("50"))
                .net(new BigDecimal("20"))
                .build();

        var result = mapper.toPosition("p1", source);

        assertThat(result.getSymbol()).isEqualTo("BTCUSD");
        assertThat(result.getEntryPrice()).isEqualByComparingTo("100");
        assertThat(result.getExposure()).isEqualByComparingTo("220");
        assertThat(result.getMargin()).isEqualByComparingTo("50");
        assertThat(result.getDataAt()).isNotNull();
    }
}
