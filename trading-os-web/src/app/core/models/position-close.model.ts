export type PositionCloseStatus =
  'CREATED' | 'SUBMITTED' | 'ACKNOWLEDGED' | 'REJECTED' | 'UNKNOWN' | 'CLOSED' | 'NOT_SUBMITTED';

export type ReconciliationResult =
  | 'EXPOSURE_CONFIRMED_ABSENT'
  | 'COMMAND_CONFIRMED_NOT_EXECUTED'
  | 'RECONCILIATION_INCONCLUSIVE'
  | null;

export interface PositionCloseResponse {
  commandId: string;
  status: PositionCloseStatus;
  externalOrderId: string | null;
  failureReason: string | null;
  resolvedMutationScope: string;
  reconciliationResult: ReconciliationResult;
}

export interface PositionCloseRequest {
  brokerPositionReference: string;
}

export interface ReconcileCloseRequest {
  // Empty body for reconciliation
}
