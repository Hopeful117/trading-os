from __future__ import annotations

from concurrent.futures import Future, ThreadPoolExecutor
from dataclasses import dataclass
from datetime import datetime, timezone
from hashlib import sha256
from threading import Event, Lock
from uuid import UUID

from app.application.service import InterpretationService
from app.domain.models import ExecutionStatus, InterpretationRequest, InterpretationResult, ProgressResponse


def now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class ExecutionRecord:
    request: InterpretationRequest
    status: ExecutionStatus
    accepted_at: datetime
    updated_at: datetime
    future: Future | None = None
    result: InterpretationResult | None = None
    request_fingerprint: str = ""
    cancel_requested: Event | None = None


class ExecutionStore:
    def __init__(self, service: InterpretationService, max_evidence: int = 256) -> None:
        self._service = service
        self._records: dict[UUID, ExecutionRecord] = {}
        self._executor = ThreadPoolExecutor(max_workers=4, thread_name_prefix="ai-engine")
        self._lock = Lock()
        self._max_evidence = max_evidence

    def submit(self, request: InterpretationRequest) -> ExecutionRecord:
        with self._lock:
            existing = self._records.get(request.execution_id)
            if existing:
                fingerprint = self._fingerprint(request)
                if existing.request_fingerprint != fingerprint:
                    raise ValueError("execution ID was reused with a different request")
                return existing
            accepted = now()
            record = ExecutionRecord(
                request, ExecutionStatus.ACCEPTED, accepted, accepted,
                request_fingerprint=self._fingerprint(request), cancel_requested=Event())
            self._records[request.execution_id] = record
            record.future = self._executor.submit(self._run, record)
            return record

    def _run(self, record: ExecutionRecord) -> None:
        with self._lock:
            record.status = ExecutionStatus.RUNNING
            record.updated_at = now()
        try:
            if record.cancel_requested and record.cancel_requested.is_set():
                raise RuntimeError("CANCELLED")
            deadline = record.accepted_at.timestamp() + record.request.policy.maximum_duration_ms / 1000
            if now().timestamp() >= deadline:
                raise TimeoutError("analysis exceeded maximum duration")
            draft = self._service.interpret(record.request, self._max_evidence)
            if record.cancel_requested and record.cancel_requested.is_set():
                raise RuntimeError("CANCELLED")
            if now().timestamp() >= deadline:
                raise TimeoutError("analysis exceeded maximum duration")
            result = InterpretationResult(
                execution_id=record.request.execution_id,
                status=ExecutionStatus.COMPLETED,
                quality="COMPLETE",
                result=draft,
                provider=self._service.provider.name,
                model=self._service.provider.model,
                accepted_at=record.accepted_at,
                completed_at=now(),
            )
        except Exception as error:  # provider boundaries must fail explicitly
            error_type = "CANCELLED" if str(error) == "CANCELLED" else type(error).__name__
            status = ExecutionStatus.CANCELLED if error_type == "CANCELLED" else ExecutionStatus.FAILED
            result = InterpretationResult(
                execution_id=record.request.execution_id,
                status=status,
                error_type=error_type,
                error_message=str(error)[:500],
                provider=self._service.provider.name,
                model=self._service.provider.model,
                accepted_at=record.accepted_at,
                completed_at=now(),
            )
        with self._lock:
            record.result = result
            record.status = result.status
            record.updated_at = result.completed_at or now()

    def progress(self, execution_id: UUID) -> ProgressResponse | None:
        with self._lock:
            record = self._records.get(execution_id)
            return None if record is None else ProgressResponse(
                execution_id=execution_id,
                status=record.status,
                updated_at=record.updated_at,
                error_type=record.result.error_type if record.result else None,
            )

    def result(self, execution_id: UUID) -> InterpretationResult | None:
        with self._lock:
            record = self._records.get(execution_id)
            return record.result if record else None

    def cancel(self, execution_id: UUID) -> bool:
        with self._lock:
            record = self._records.get(execution_id)
            if not record or record.status in {
                ExecutionStatus.COMPLETED,
                ExecutionStatus.FAILED,
                ExecutionStatus.CANCELLED,
            }:
                return False
            if record.cancel_requested:
                record.cancel_requested.set()
            if record.future and record.future.cancel():
                record.status = ExecutionStatus.CANCELLED
                record.updated_at = now()
                return True
            if record.status in {ExecutionStatus.ACCEPTED, ExecutionStatus.RUNNING}:
                return True
            return False

    @staticmethod
    def _fingerprint(request: InterpretationRequest) -> str:
        return sha256(request.model_dump_json().encode("utf-8")).hexdigest()
