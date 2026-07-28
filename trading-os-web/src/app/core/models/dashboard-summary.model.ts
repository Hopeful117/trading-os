export type DashboardDataStatus = 'LIVE' | 'DEGRADED' | 'STALE' | 'UNAVAILABLE';
export type DashboardAlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL';
export type PositionProtectionStatus = 'PROTECTED' | 'MISSING_STOP_LOSS' | 'UNKNOWN';
export type RiskStatus = 'SAFE' | 'WARNING' | 'BREACHED' | 'UNAVAILABLE';
export type PositionSide = 'BUY' | 'SELL';

export interface AccountDashboardSummary {
  accountId: string;
  accountName: string;
  broker: string;
  currency: string;
  balance: number | null;
  equity: number | null;
  dailyPnl: number | null;
  dailyPnlPercentage: number | null;
  currentDrawdown: number | null;
  currentDrawdownPercentage: number | null;
  equitySource: string;
}

export interface OpenPositionDashboardView {
  positionId: string;
  accountId: string;
  marketId: string | null;
  symbol: string;
  side: PositionSide;
  quantity: number;
  entryPrice: number;
  currentPrice: number | null;
  stopLoss: number | null;
  takeProfit: number | null;
  unrealizedPnl: number | null;
  unrealizedPnlPercentage: number | null;
  brokerUnrealizedPnl: number | null;
  riskAmount: number;
  riskPercentage: number;
  exposure: number;
  protectionStatus: PositionProtectionStatus;
  marketTradable: boolean;
  openedAt: string | null;
  priceOccurredAt: string | null;
  calculatedAt: string;
}

export interface RiskRuleDashboardView {
  code: string;
  label: string;
  limit: number;
  currentValue: number;
  status: RiskStatus;
}

export interface RiskDashboardSummary {
  status: RiskStatus;
  usedRiskAmount: number;
  usedRiskPercentage: number;
  remainingRiskAmount: number;
  remainingRiskPercentage: number;
  dailyLossAmount: number;
  dailyLossPercentage: number;
  maximumDailyLossPercentage: number;
  totalDrawdownAmount: number;
  totalDrawdownPercentage: number;
  maximumDrawdownPercentage: number;
  rules: RiskRuleDashboardView[];
}

export interface DashboardAlert {
  code: string;
  severity: DashboardAlertSeverity;
  title: string;
  message: string;
  marketId: string | null;
  positionId: string | null;
  occurredAt: string;
}

export interface DashboardFreshness {
  status: DashboardDataStatus;
  brokerDataAt: string | null;
  marketDataAt: string | null;
  calculatedAt: string;
  brokerDataStale: boolean;
  marketDataStale: boolean;
  warnings: string[];
}

export interface MarketDashboardView {
  marketId: string;
  symbol: string;
  lastPrice: number | null;
  tradable: boolean;
  occurredAt: string | null;
}

export interface DashboardSummary {
  account: AccountDashboardSummary;
  risk: RiskDashboardSummary;
  openPositions: OpenPositionDashboardView[];
  alerts: DashboardAlert[];
  watchedMarkets: MarketDashboardView[];
  freshness: DashboardFreshness;
  generatedAt: string;
}
