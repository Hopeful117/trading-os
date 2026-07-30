/**
 * ADR-027 orchestration. TradePlanningEngine is the sole aggregate creation path;
 * policies remain deterministic and broker/risk/execution integrations are ports.
 */
package com.hope.trading.market_intelligence.application.tradeplan;
