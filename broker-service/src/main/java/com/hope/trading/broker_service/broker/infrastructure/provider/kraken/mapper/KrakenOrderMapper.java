package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public final class KrakenOrderMapper {
    public OrderSnapshot order(String id,JsonNode value,Instant observedAt){
        BigDecimal volume=decimal(value,"vol"),executed=decimal(value,"vol_exec");
        return new OrderSnapshot(id,text(value,"cl_ord_id"),status(value.path("status").asText()),volume,executed,java.util.List.of(),observedAt);
    }
    public OrderStatus status(String value){return switch(value){case "pending"->OrderStatus.ACKNOWLEDGED;case "open"->OrderStatus.OPEN;case "closed"->OrderStatus.FILLED;case "canceled","expired"->OrderStatus.CANCELLED;default->OrderStatus.UNKNOWN;};}
    private BigDecimal decimal(JsonNode node,String field){String value=node.path(field).asText("0");return new BigDecimal(value);}
    private String text(JsonNode node,String field){String value=node.path(field).asText();return value.isBlank()?null:value;}
}
