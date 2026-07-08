package com.hope.trading.broker_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KrakenTickerResult {
    private List<String> a; // Ask
    private List<String> b; // Bid
    private List<String> c; // Last trade closed
    private List<String> v; // Volume
    private List<String> p; // VWAP price

}
