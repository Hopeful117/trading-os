export type ExecutionStatus =
  | 'CREATED'
  | 'VALIDATED'
  | 'SUBMISSION_IN_PROGRESS'
  | 'SUBMISSION_OUTCOME_UNKNOWN'
  | 'RECONCILIATION_IN_PROGRESS'
  | 'COMPLETED'
  | 'FAILED'
  | 'RECOVERY_BLOCKED'
  | 'CANCELLED'
  | 'EXPIRED';

export const TERMINAL_STATUSES: ReadonlySet<ExecutionStatus> = new Set([
  'COMPLETED',
  'CANCELLED',
  'EXPIRED',
]);

export const POLLABLE_STATUSES: ReadonlySet<ExecutionStatus> = new Set([
  'CREATED',
  'VALIDATED',
  'SUBMISSION_IN_PROGRESS',
  'SUBMISSION_OUTCOME_UNKNOWN',
  'RECONCILIATION_IN_PROGRESS',
  'RECOVERY_BLOCKED',
]);

export interface ValidateExecutionRequest {
  tradePlanId: string;
  tradePlanVersion: number;
  evaluationId: string;
  brokerAccountId: string;
  expiresAt: string;
}

export interface ExecutionDto {
  id: string;
  tradePlanId: string;
  tradePlanVersion: number;
  riskEvaluationId: string;
  idempotencyKey: string;
  brokerAccountId: string;
  status: ExecutionStatus;
  createdAt: string;
  updatedAt: string;
  expiresAt: string;
  version: number;
  brokerExternalOrderId: string | null;
  brokerOrderStatus: string | null;
  filledQuantity: number | null;
  averageFillPrice: number | null;
  totalFees: number | null;
  failureReason: string | null;
}

export function isTerminal(status: ExecutionStatus): boolean {
  return TERMINAL_STATUSES.has(status);
}

export function shouldPoll(status: ExecutionStatus): boolean {
  return POLLABLE_STATUSES.has(status);
}
