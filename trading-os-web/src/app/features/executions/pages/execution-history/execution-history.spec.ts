import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ExecutionDto, ExecutionSummaryDto } from '../../../../core/models/execution.model';
import { TradePlanResponse } from '../../../../core/models/trade-plan.model';
import { ExecutionService } from '../../../../core/services/execution.service';
import { TradePlanService } from '../../../../core/services/trade-plan.service';
import { ExecutionHistory } from './execution-history';

describe('ExecutionHistory', () => {
  let fixture: ComponentFixture<ExecutionHistory>;
  let executionService: { list: ReturnType<typeof vi.fn>; getExecution: ReturnType<typeof vi.fn> };
  let tradePlanService: { getPlan: ReturnType<typeof vi.fn> };

  const summary: ExecutionSummaryDto = {
    id: 'execution-1',
    tradePlanId: 'plan-1',
    tradePlanVersion: 4,
    status: 'COMPLETED',
    updatedAt: '2026-09-20T12:00:00Z',
  };

  const execution = {
    id: 'execution-1',
    tradePlanId: 'plan-1',
    tradePlanVersion: 2,
    riskEvaluationId: 'evaluation-1',
    idempotencyKey: 'key-1',
    brokerAccountId: 'broker-1',
    status: 'COMPLETED',
    createdAt: '2026-09-20T11:00:00Z',
    updatedAt: '2026-09-20T12:00:00Z',
    expiresAt: '2026-09-20T13:00:00Z',
    version: 1,
    brokerExternalOrderId: 'order-1',
    brokerOrderStatus: 'FILLED',
    filledQuantity: 10,
    averageFillPrice: 100,
    totalFees: 1,
    failureReason: null,
  } as ExecutionDto;

  const plan = {
    id: 'plan-1',
    version: 2,
    tradingAccountId: 'account-1',
    instrument: 'BTC/USD',
    direction: 'LONG',
  } as TradePlanResponse;

  beforeEach(async () => {
    executionService = {
      list: vi.fn(() => of([summary])),
      getExecution: vi.fn(() => of(execution)),
    };
    tradePlanService = { getPlan: vi.fn(() => of(plan)) };

    await TestBed.configureTestingModule({
      imports: [ExecutionHistory],
      providers: [
        provideRouter([]),
        { provide: ExecutionService, useValue: executionService },
        { provide: TradePlanService, useValue: tradePlanService },
      ],
    }).compileComponents();
  });

  async function create(): Promise<void> {
    fixture = TestBed.createComponent(ExecutionHistory);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function text(): string {
    return fixture.nativeElement.textContent;
  }

  it('renders owned executions and enriches a row with continuity links', async () => {
    await create();
    expect(text()).toContain('Execution executio');
    expect(text()).toContain('Terminée');

    fixture.nativeElement.querySelector('[data-testid="load-details-button"]').click();
    fixture.detectChanges();

    expect(executionService.getExecution).toHaveBeenCalledWith('execution-1');
    expect(tradePlanService.getPlan).toHaveBeenCalledWith('plan-1', 2);
    expect(text()).toContain('BTC/USD');
    expect(fixture.nativeElement.querySelector('[data-testid="trade-plan-link"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="positions-link"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="retry-button"]')).toBeNull();
  });

  it('preserves uncertain execution semantics without retry actions', async () => {
    executionService.list.mockReturnValue(
      of([{ ...summary, status: 'SUBMISSION_OUTCOME_UNKNOWN' }]),
    );
    await create();

    expect(text()).toContain('Résultat incertain');
    expect(text()).toContain('outcome is not confirmed');
    expect(fixture.nativeElement.querySelector('[data-testid="retry-button"]')).toBeNull();
  });

  it('renders empty and error states', async () => {
    executionService.list.mockReturnValue(of([]));
    await create();
    expect(text()).toContain('No executions have been recorded yet.');

    executionService.list.mockReturnValue(throwError(() => new Error('down')));
    fixture = TestBed.createComponent(ExecutionHistory);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text()).toContain('temporarily unavailable');
  });

  it('renders the loading state while the owned list is pending', async () => {
    executionService.list.mockReturnValue(new Observable(() => {}));
    await create();

    expect(text()).toContain('Loading executions…');
  });

  it('keeps the execution visible when detail enrichment fails', async () => {
    executionService.getExecution.mockReturnValue(throwError(() => new Error('down')));
    await create();
    fixture.nativeElement.querySelector('[data-testid="load-details-button"]').click();
    fixture.detectChanges();

    expect(text()).toContain('Execution executio');
    expect(text()).toContain('owned record remains visible');
  });
});
