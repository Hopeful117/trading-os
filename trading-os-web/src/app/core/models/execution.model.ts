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
}
