package com.hope.trading.broker_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class KrakenOpenPositionResult {
    private String ordertxid;

    private String posstatus;

    private String pair;

    private BigDecimal time;

    private String type;

    private String ordertype;

    private BigDecimal cost;

    private BigDecimal fee;

    private BigDecimal vol;

    private BigDecimal volClosed;

    private BigDecimal margin;

    private BigDecimal value;

    private BigDecimal net;

    private String terms;

    private BigDecimal rolloverTm;

    private String misc;

    private String oflags;
}

