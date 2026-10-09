package com.hope.trading.broker_service.broker;

import com.hope.trading.broker_service.broker.api.dto.BrokerApiDtos.ExecutionApiRequest;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BrokerApiContractTest {
    @Test void acceptsTheTradingCoreExecutionContract(){var request=new ExecutionApiRequest(UUID.randomUUID(),UUID.randomUUID(),"stable-key",UUID.randomUUID(),"XBTUSD",Side.BUY,OrderType.MARKET,BigDecimal.ONE,null);try(var factory=Validation.buildDefaultValidatorFactory()){assertThat(factory.getValidator().validate(request)).isEmpty();}assertThat(request.toModel().idempotencyKey()).isEqualTo("stable-key");}
    @Test void rejectsAnIncompleteExecutionContract(){var request=new ExecutionApiRequest(null,null,"",null,"",null,null,BigDecimal.ZERO,null);try(var factory=Validation.buildDefaultValidatorFactory()){assertThat(factory.getValidator().validate(request)).hasSizeGreaterThanOrEqualTo(8);}}
    @Test void serializesTechnicalCapabilitiesAsBrokerNeutralFacts(){var value=new TechnicalCapabilities(UUID.randomUUID(),"KRAKEN","BTC/USD",1,
            java.time.Instant.parse("2026-08-01T00:00:00Z"),java.util.List.of(OrderType.MARKET),
            java.util.List.of(BigDecimal.ONE,new BigDecimal("2")),java.util.List.of(BigDecimal.ONE));try{String json=new ObjectMapper().findAndRegisterModules().writeValueAsString(value);assertThat(json).contains("supportedBuyLeverageLevels","supportedSellLeverageLevels").doesNotContain("leverage_buy","leverage_sell");}catch(Exception exception){throw new AssertionError(exception);}}
}
