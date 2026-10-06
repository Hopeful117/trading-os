import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { RiskDecisionResponse, TradePlanResponse } from '../../../core/models/trade-plan.model';
import { TradePlanService } from '../../../core/services/trade-plan.service';
import { ExecutionService } from '../../../core/services/execution.service';
import { PlanPage } from './plan-page';

function fakePlan(status: string): TradePlanResponse {
  return {
    id: 'tp-1',
    version: 1,
    previousVersion: null,
    status,
    planningContextId: 'ctx-1',
    planningContextVersion: 1,
    contextCapturedAt: '2025-01-01T00:00:00Z',
    instrument: 'BTC/EUR',
    direction: 'LONG',
    entryType: 'MARKET',
    entryPrice: 100,
    stopLoss: 95,
    takeProfits: [110, 120],
    quantity: 1,
    notional: 100,
    monetaryRisk: 5,
    riskReward: 2.0,
    expiresAt: '2025-01-02T00:00:00Z',
    thesis: 'Breakout above resistance',
    opportunityIds: [],
    observationIds: [],
    aiAnalysisIds: [],
    confirmationConditions: [],
    invalidationConditions: [],
    managementRules: [],
    createdAt: '2025-01-01T00:00:00Z',
    tradingAccountId: 'acc-1',
  };
}

function fakeRiskDecision(decision: string): RiskDecisionResponse {
  return {
    evaluationId: 'eval-1',
    tradePlanId: 'tp-1',
    tradePlanVersion: 1,
    accountId: 'acc-1',
    status: 'COMPLETED',
    decision: decision as RiskDecisionResponse['decision'],
    approved: decision !== 'REJECTED',
    reasons: [],
    warnings: [],
    evaluatedAt: '2025-01-01T00:00:00Z',
  };
}

function mockActivatedRoute(params: Record<string, string>) {
  return {
    paramMap: of({
      get: (key: string) => params[key] ?? null,
      has: (key: string) => key in params,
      getAll: () => [],
      keys: Object.keys(params),
    }),
  };
}

describe('PlanPage', () => {
  let fixture: ComponentFixture<PlanPage>;

  function configureMocks(
    plan$: Observable<TradePlanResponse> = of(fakePlan('PROPOSED')),
    decide$: Observable<TradePlanResponse> = of(fakePlan('ACCEPTED')),
    risk$: Observable<RiskDecisionResponse> = of(fakeRiskDecision('APPROVED')),
    validate$: Observable<unknown> = of({ id: 'exec-1', status: 'VALIDATED' }),
    params: Record<string, string> = { planId: 'tp-1', version: '1' },
  ) {
    const tradePlanService = {
      getPlan: () => plan$,
      decide: () => decide$,
      evaluateRisk: () => risk$,
    };
    const executionService = {
      validate: () => validate$,
      execute: () => of({ id: 'exec-1', status: 'COMPLETED' } as any),
      list: () => of([]),
      getExecution: () => of({ id: 'exec-1', status: 'CREATED' } as any),
    };
    return TestBed.configureTestingModule({
      imports: [PlanPage],
      providers: [
        { provide: ActivatedRoute, useValue: mockActivatedRoute(params) },
        { provide: TradePlanService, useValue: tradePlanService },
        { provide: ExecutionService, useValue: executionService },
      ],
    });
  }

  it('shows proposal state with accept/reject buttons', () => {
    configureMocks();
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    const card = fixture.nativeElement.querySelector('[data-testid="proposal-state"]');
    expect(card).toBeTruthy();
    expect(card.textContent).toContain('BTC/EUR');
    expect(fixture.nativeElement.querySelector('[data-testid="accept-button"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="reject-button"]')).toBeTruthy();
  });

  it('shows accepted state with evaluate risk button', () => {
    configureMocks(of(fakePlan('ACCEPTED')));
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="accepted-state"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="evaluate-risk-button"]'),
    ).toBeTruthy();
  });

  it('shows rejected state', () => {
    configureMocks(of(fakePlan('REJECTED')));
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="rejected-state"]')).toBeTruthy();
  });

  it('shows execution-ready state for APPROVED risk decision', () => {
    configureMocks(
      of(fakePlan('ACCEPTED')),
      of(fakePlan('ACCEPTED')),
      of(fakeRiskDecision('APPROVED')),
    );
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="evaluate-risk-button"]')?.click();
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="execution-ready-state"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="execute-button"]')).toBeTruthy();
  });

  it('clicking execute triggers execution flow and shows result', () => {
    configureMocks(
      of(fakePlan('ACCEPTED')),
      of(fakePlan('ACCEPTED')),
      of(fakeRiskDecision('APPROVED')),
    );
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="evaluate-risk-button"]')?.click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="execute-button"]')?.click();
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="execution-result-state"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="execution-context"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('BTC/EUR');
    expect(fixture.nativeElement.textContent).toContain('acc-1');
    expect(fixture.nativeElement.querySelector('[data-testid="positions-link"]')).toBeTruthy();
  });

  it('retries T1 on the existing execution without validating a new intent', () => {
    const validate = vi.fn(() => of({ id: 'exec-1', status: 'VALIDATED' }));
    const execute = vi.fn(() => of({ id: 'exec-1', status: 'RISK_REVALIDATION_UNAVAILABLE' }));
    const retryT1 = vi.fn(() => of({ id: 'exec-1', status: 'COMPLETED' }));
    const plan = fakePlan('ACCEPTED');

    TestBed.configureTestingModule({
      imports: [PlanPage],
      providers: [
        { provide: ActivatedRoute, useValue: mockActivatedRoute({ planId: 'tp-1', version: '1' }) },
        {
          provide: TradePlanService,
          useValue: {
            getPlan: () => of(plan),
            decide: () => of(plan),
            evaluateRisk: () => of(fakeRiskDecision('APPROVED')),
          },
        },
        {
          provide: ExecutionService,
          useValue: {
            validate,
            execute,
            retryT1,
            list: () => of([]),
            getExecution: () => of({ id: 'exec-1', status: 'CREATED' }),
          },
        },
      ],
    });

    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="evaluate-risk-button"]')?.click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="execute-button"]')?.click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="retry-t1-button"]')?.click();
    fixture.detectChanges();

    expect(validate).toHaveBeenCalledTimes(1);
    expect(execute).toHaveBeenCalledTimes(1);
    expect(retryT1).toHaveBeenCalledWith('exec-1');
  });

  it('does not show execute button for REJECTED risk decision', () => {
    configureMocks(
      of(fakePlan('ACCEPTED')),
      of(fakePlan('ACCEPTED')),
      of(fakeRiskDecision('REJECTED')),
    );
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="evaluate-risk-button"]')?.click();
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="execution-ready-state"]'),
    ).toBeFalsy();
    expect(fixture.nativeElement.querySelector('[data-testid="execute-button"]')).toBeFalsy();
  });

  it('renders persisted risk-validated plans without offering a new risk evaluation', () => {
    configureMocks(of(fakePlan('RISK_VALIDATED')));
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="risk-validated-state"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="evaluate-risk-button"]')).toBeFalsy();
  });

  it('resumes an existing authorized execution intent without evaluating risk again', () => {
    const plan = fakePlan('READY_TO_EXECUTE');
    const evaluateRisk = vi.fn(() => of(fakeRiskDecision('APPROVED')));
    const execute = vi.fn(() => of({ id: 'exec-1', status: 'COMPLETED' } as any));
    const tradePlanService = {
      getPlan: () => of(plan),
      decide: () => of(plan),
      evaluateRisk,
    };
    TestBed.configureTestingModule({
      imports: [PlanPage],
      providers: [
        { provide: ActivatedRoute, useValue: mockActivatedRoute({ planId: 'tp-1', version: '4' }) },
        { provide: TradePlanService, useValue: tradePlanService },
        {
          provide: ExecutionService,
          useValue: {
            list: () =>
              of([{ id: 'exec-1', tradePlanId: 'tp-1', tradePlanVersion: 1, status: 'CREATED' }]),
            getExecution: () => of({ id: 'exec-1', status: 'CREATED' }),
            execute,
          },
        },
      ],
    });

    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.nativeElement
      .querySelector('[data-testid="resume-authorized-execution-button"]')
      ?.click();
    fixture.detectChanges();

    expect(evaluateRisk).not.toHaveBeenCalled();
    expect(execute).toHaveBeenCalledWith('exec-1');
  });

  it('renders a retryable message when loading fails with a conflict', () => {
    configureMocks(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { code: 'VERSION_CONFLICT' },
          }),
      ),
    );
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('trade plan changed');
    expect(fixture.nativeElement.querySelector('[data-testid="retry-load-button"]')).toBeTruthy();
  });

  it('does not allow a second decision while the first decision is pending', () => {
    const decide = vi.fn(() => new Observable<TradePlanResponse>(() => {}));
    const tradePlanService = {
      getPlan: () => of(fakePlan('PROPOSED')),
      decide,
      evaluateRisk: () => of(fakeRiskDecision('APPROVED')),
    };
    TestBed.configureTestingModule({
      imports: [PlanPage],
      providers: [
        { provide: ActivatedRoute, useValue: mockActivatedRoute({ planId: 'tp-1', version: '1' }) },
        { provide: TradePlanService, useValue: tradePlanService },
        {
          provide: ExecutionService,
          useValue: {
            validate: () => of({ id: 'exec-1', status: 'COMPLETED' }),
            execute: () => of({}),
          },
        },
      ],
    });
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="accept-button"]')?.click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="accept-button"]')?.click();

    expect(decide).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('[data-testid="deciding-state"]')).toBeTruthy();
  });

  it('shows an error when the plan reference is invalid', () => {
    configureMocks(
      of(fakePlan('PROPOSED')),
      of(fakePlan('ACCEPTED')),
      of(fakeRiskDecision('APPROVED')),
      of({}),
      {},
    );
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('reference is invalid');
  });

  it('renders an error when accepting or rejecting a plan fails', () => {
    configureMocks(
      of(fakePlan('PROPOSED')),
      throwError(() => new Error('down')),
    );
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="accept-button"]')?.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('decision could not be recorded');
  });

  it('renders an error when risk evaluation fails or the plan has no account', () => {
    const planWithoutAccount = { ...fakePlan('ACCEPTED'), tradingAccountId: '' };
    configureMocks(of(planWithoutAccount));
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="evaluate-risk-button"]')?.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('no trading account');
  });

  it('renders an error when risk evaluation fails', () => {
    configureMocks(
      of(fakePlan('ACCEPTED')),
      of(fakePlan('ACCEPTED')),
      throwError(() => new Error('down')),
    );
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="evaluate-risk-button"]')?.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Risk evaluation could not be completed');
  });

  it('renders an error when execution validation fails', () => {
    configureMocks(
      of(fakePlan('ACCEPTED')),
      of(fakePlan('ACCEPTED')),
      of(fakeRiskDecision('APPROVED')),
      throwError(() => new Error('down')),
    );
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="evaluate-risk-button"]')?.click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="execute-button"]')?.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('trade could not be submitted');
  });

  it('formats known and unknown execution statuses', () => {
    configureMocks();
    fixture = TestBed.createComponent(PlanPage);

    expect(fixture.componentInstance.statusLabel('COMPLETED')).toBe('Accepted by broker');
    expect(fixture.componentInstance.statusLabel('FAILED')).toBe('Execution failed');
    expect(fixture.componentInstance.brokerOrderLabel('FILLED')).toBe('Filled');
    expect(fixture.componentInstance.brokerOrderLabel('CUSTOM')).toBe('CUSTOM');
    expect(fixture.componentInstance.brokerOrderLabel(null)).toBe('');
  });
});
