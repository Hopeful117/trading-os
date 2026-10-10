import { HttpErrorResponse } from '@angular/common/http';
import { RiskReason } from '../models/trade-plan.model';

export interface TradePreparationFailure {
  code: string;
  message: string;
  retryable: boolean;
  reasons: RiskReason[];
  warnings: RiskReason[];
  metrics: Record<string, number>;
}

export function tradePreparationFailure(error: unknown): TradePreparationFailure | null {
  if (!(error instanceof HttpErrorResponse) || typeof error.error?.code !== 'string') {
    return null;
  }
  const body = error.error as Partial<TradePreparationFailure>;
  if (body.code !== 'RISK_PREFLIGHT_REJECTED' && body.code !== 'RISK_PREFLIGHT_UNAVAILABLE') {
    return null;
  }
  return {
    code: body.code,
    message: typeof body.message === 'string' ? body.message : 'Risk preflight failed.',
    retryable: body.retryable !== false,
    reasons: Array.isArray(body.reasons) ? body.reasons : [],
    warnings: Array.isArray(body.warnings) ? body.warnings : [],
    metrics: body.metrics && typeof body.metrics === 'object' ? body.metrics : {},
  };
}

export function tradeFlowErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallback;
  }

  const code = error.error?.code;
  switch (code) {
    case 'OPPORTUNITY_NOT_ELIGIBLE':
    case 'TRADE_PLAN_EXPIRED':
      return 'This opportunity or trade plan is no longer active.';
    case 'TRADE_PLAN_NOT_FOUND':
    case 'OPPORTUNITY_NOT_FOUND':
      return 'The requested trading item could not be found.';
    case 'VERSION_CONFLICT':
    case 'STALE_VERSION':
    case 'VERSION_MISMATCH':
      return 'This trade plan changed. Reload it before trying again.';
    case 'ACCOUNT_FORBIDDEN':
    case 'BROKER_ACCOUNT_FORBIDDEN':
      return 'This trading account is not available to the authenticated user.';
    case 'DECISION_NOT_AUTHORIZED':
    case 'RISK_DECISION_REJECTED':
      return 'The deterministic risk decision does not authorize this trade.';
    case 'RISK_PREFLIGHT_REJECTED':
      return 'The proposed trade is not feasible under the current risk limits. Review the blocking rules and try again later.';
    case 'RISK_PREFLIGHT_UNAVAILABLE':
      return 'The current risk context is unavailable. No trade plan was prepared. Try again later.';
  }

  if (error.status === 404) return 'The requested trading item could not be found.';
  if (error.status === 403) return 'This trading action is not authorized for the current user.';
  if (error.status === 409) return 'This trade plan changed. Reload it before trying again.';
  if (error.status === 422) return 'The trading action was rejected by the current business rules.';
  if (error.status >= 500 || error.status === 0) {
    return 'Trading services are temporarily unavailable. Try again later.';
  }

  return fallback;
}
