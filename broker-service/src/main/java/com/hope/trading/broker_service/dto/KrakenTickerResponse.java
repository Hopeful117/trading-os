package com.hope.trading.broker_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KrakenTickerResponse {
    private List<String> error;

    private Map<String, KrakenTickerResult> result;

}
