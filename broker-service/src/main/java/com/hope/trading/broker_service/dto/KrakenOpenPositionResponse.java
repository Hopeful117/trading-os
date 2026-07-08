package com.hope.trading.broker_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KrakenOpenPositionResponse {
    List<String>errors;
    Map<String,KrakenOpenPositionResult>results;

}
