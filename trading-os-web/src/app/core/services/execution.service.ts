import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ExecutionDto, ValidateExecutionRequest } from '../models/execution.model';

@Injectable({
  providedIn: 'root',
})
export class ExecutionService {
  private http = inject(HttpClient);

  validate(request: ValidateExecutionRequest, idempotencyKey: string): Observable<ExecutionDto> {
    return this.http.post<ExecutionDto>(
      `${environment.gatewayUrl}v1/executions/validate`,
      request,
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
  }

  execute(executionId: string): Observable<ExecutionDto> {
    return this.http.post<ExecutionDto>(
      `${environment.gatewayUrl}v1/executions/${executionId}/execute`,
      {},
    );
  }

  getExecution(executionId: string): Observable<ExecutionDto> {
    return this.http.get<ExecutionDto>(`${environment.gatewayUrl}v1/executions/${executionId}`);
  }
}
