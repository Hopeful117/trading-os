import { OpenPositionDashboardView } from './dashboard-summary.model';

export type PositionIntelligenceStatus = 'AVAILABLE' | 'PARTIAL' | 'UNKNOWN' | 'UNAVAILABLE';

export interface PositionIntelligenceView {
  position: OpenPositionDashboardView;
  status: PositionIntelligenceStatus;
  lineage: PositionIntelligenceLineage;
  rationale: PositionIntelligenceRationale;
  comparisons: PositionIntelligenceComparisons;
  scenarios: PositionScenarioProjection;
  accountData: PositionIntelligenceSourceStatus;
  marketData: PositionIntelligenceSourceStatus;
  challengeData: PositionIntelligenceSourceStatus;
  generatedAt: string;
}

export interface PositionIntelligenceSourceStatus {
  status: string;
  observedAt: string | null;
  reason: string | null;
}

export interface PositionIntelligenceLineage {
  status: PositionIntelligenceStatus;
  tradeOutcomeId: string | null;
  executionIntentId: string | null;
  tradePlanId: string | null;
  tradePlanVersion: number | null;
  origin: string | null;
  opportunityIds: string[];
  reason: string | null;
}

export interface PositionIntelligenceRationale {
  status: PositionIntelligenceStatus;
  thesis: string | null;
  confirmationConditions: string[];
  invalidationConditions: string[];
  managementRules: string[];
  reason: string | null;
}

export interface PositionIntelligenceComparisons {
  status: PositionIntelligenceStatus;
  priceChange: number | null;
  priceChangePercentage: number | null;
  distanceToStop: number | null;
  distanceToTarget: number | null;
  unavailableReasons: string[];
}

export interface PositionScenarioProjection {
  tradePlanId: string | null;
  tradePlanVersion: number;
  accountId: string;
  observedAt: string | null;
  projectedAt: string;
  economicsBasis: string;
  current: PositionScenario;
  stop: PositionScenario;
  target: PositionScenario;
  economics: PositionScenarioEconomics;
  challenge: PositionScenarioChallenge;
}

export interface PositionScenario {
  type: string;
  availability: string;
  reasons: string[];
  projectedPnl: number | null;
  projectedBalance: number | null;
  projectedEquity: number | null;
  dailyLossRemaining: number | null;
  totalLossRemaining: number | null;
  challengeValue: number | null;
  challengeTargetRemaining: number | null;
}

export interface PositionScenarioEconomics {
  availability: string;
  reasons: string[];
  monetaryRisk: number | null;
  monetaryReward: number | null;
  riskReward: number | null;
}

export interface PositionScenarioChallenge {
  availability: string;
  reasons: string[];
  currentValue: number | null;
  targetValue: number | null;
  targetRemaining: number | null;
}
