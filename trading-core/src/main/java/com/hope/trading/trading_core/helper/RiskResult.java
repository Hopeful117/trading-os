package com.hope.trading.trading_core.helper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class RiskResult {
    private final boolean allowed;
    private final String message;

    public static RiskResult allowed() {
      return new RiskResult(true, "Trade allowed");
    }

    public static RiskResult reject(String reason){
        return new RiskResult(false, reason);
    }

}
