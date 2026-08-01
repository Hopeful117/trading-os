package com.hope.trading.broker_service.broker;

import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import java.math.BigDecimal;import java.util.UUID;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;

class BrokerModelsTest {
    @Test void limitOrderRequiresAPrice(){assertThatThrownBy(()->new ExecutionRequest(UUID.randomUUID(),UUID.randomUUID(),"key",UUID.randomUUID(),"EURUSD",Side.BUY,OrderType.LIMIT,BigDecimal.ONE,null)).isInstanceOf(IllegalArgumentException.class);}
    @Test void publicModelsAreImmutableRecords(){assertThat(ExecutionRequest.class.isRecord()).isTrue();assertThat(AccountSnapshot.class.isRecord()).isTrue();assertThat(OrderSnapshot.class.isRecord()).isTrue();}
}
