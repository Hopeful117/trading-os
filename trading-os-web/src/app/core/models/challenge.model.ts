import { HttpErrorResponse } from '@angular/common/http';

export type ChallengeStatus = 'ACTIVE' | 'PASSED' | 'BREACHED';
export type ProgressionValueSource = 'BALANCE' | 'EQUITY';
export type SettlementModel = 'CASH' | 'INVENTORY';
export type InstrumentProductType = 'SPOT' | 'LINEAR_PERPETUAL' | 'INVERSE_PERPETUAL';

export interface ChallengeDefinition {
  id: string;
  definitionVersion: string;
  provider: string;
  product: string;
  planCode: string;
  capitalCurrency: string;
  startingCapital: number;
  profitTargetRatio: number;
  progressionValueSource: ProgressionValueSource;
  requiredSettlementModel: SettlementModel | null;
  requiredInstrumentProductType: InstrumentProductType | null;
  riskPolicyId: string;
  riskPolicyVersion: string;
  sourceUrl: string;
  retrievedAt: string;
  effectiveFrom: string;
  provenance: string;
}

export interface StartChallengeRequest {
  definitionId: string;
  definitionVersion: string;
}

export interface ChallengeInstance {
  id: string;
  accountId: string;
  definitionId: string;
  definitionVersion: string;
  riskPolicyId: string;
  riskPolicyVersion: string;
  startingCapital: number;
  capitalCurrency: string;
  startedAt: string;
  status: ChallengeStatus;
  passedAt: string | null;
  breachedAt: string | null;
  terminalReason: string | null;
  terminalRiskEvaluationId: string | null;
}

export interface ChallengeProgress {
  challengeId: string;
  accountId: string;
  status: ChallengeStatus;
  startingCapital: number;
  currentValue: number | null;
  targetValue: number;
  currentProfit: number | null;
  targetProgress: number | null;
  dailyReferenceBalance: number | null;
  dailyLossRemaining: number | null;
  totalDrawdownRemaining: number | null;
  evaluatedAt: string | null;
  passedAt: string | null;
  breachedAt: string | null;
  terminalReason: string | null;
  terminalRiskEvaluationId: string | null;
}

export interface ChallengeProblemDetail {
  code?: string;
  detail?: string;
  title?: string;
  status?: number;
}

export function challengeErrorCode(error: unknown): string | null {
  if (!(error instanceof HttpErrorResponse) || typeof error.error?.code !== 'string') {
    return null;
  }
  return error.error.code;
}

export function challengeErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallback;
  }

  const detail = error.error?.detail;
  if (typeof detail === 'string' && detail.length > 0) {
    return detail;
  }
  if (error.status === 409) {
    return 'Un Challenge actif existe déjà pour ce compte.';
  }
  if (error.status >= 500 || error.status === 0) {
    return 'Le service Challenge est temporairement indisponible.';
  }
  if (error.status === 404) {
    return 'Les données Challenge ne sont pas disponibles pour ce compte.';
  }
  return fallback;
}
