import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TradePlanApprovalResponse } from '../../../core/models/trade-plan.model';
import { TradePlanService } from '../../../core/services/trade-plan.service';
import { ApprovalPage } from './approval-page';

describe('ApprovalPage', () => {
  let fixture: ComponentFixture<ApprovalPage>;
  let service: {
    getApproval: ReturnType<typeof vi.fn>;
    decideApproval: ReturnType<typeof vi.fn>;
  };

  const routeFor = (challengeId: string | null): ActivatedRoute => {
    const paramMap = convertToParamMap(challengeId ? { challengeId } : {});
    return { paramMap: of(paramMap), snapshot: { paramMap } } as unknown as ActivatedRoute;
  };

  beforeEach(async () => {
    service = {
      getApproval: vi.fn(),
      decideApproval: vi.fn(() => of(approval('APPROVED'))),
    };
    await TestBed.configureTestingModule({
      imports: [ApprovalPage],
      providers: [
        { provide: ActivatedRoute, useValue: routeFor('challenge-1') },
        { provide: TradePlanService, useValue: service },
      ],
    }).compileComponents();
  });

  it('renders a pending challenge and submits an approval decision', async () => {
    service.getApproval.mockReturnValue(of(approval('PENDING')));
    fixture = TestBed.createComponent(ApprovalPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Review trade plan');
    expect(fixture.nativeElement.textContent).toContain('EUR/USD');
    (fixture.nativeElement.querySelector('.approve') as HTMLButtonElement).click();

    expect(service.decideApproval).toHaveBeenCalledWith('challenge-1', 'approve');
  });

  it('renders a terminal challenge and supports rejection decisions', async () => {
    service.getApproval.mockReturnValue(of(approval('APPROVED')));
    fixture = TestBed.createComponent(ApprovalPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Challenge status: APPROVED');
    expect(fixture.nativeElement.querySelector('.approve')).toBeNull();

    fixture.componentInstance.decide('reject');
    expect(service.decideApproval).toHaveBeenCalledWith('challenge-1', 'reject');
  });

  it('renders errors for missing and unavailable challenges', async () => {
    service.getApproval.mockReturnValue(throwError(() => new Error('not found')));
    fixture = TestBed.createComponent(ApprovalPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Approval unavailable');

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ApprovalPage],
      providers: [
        { provide: ActivatedRoute, useValue: routeFor(null) },
        { provide: TradePlanService, useValue: service },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ApprovalPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Missing approval challenge.');
    fixture.componentInstance.decide('approve');
    expect(service.decideApproval).not.toHaveBeenCalled();
  });
});

function approval(status: TradePlanApprovalResponse['status']): TradePlanApprovalResponse {
  return {
    challengeId: 'challenge-1',
    status,
    tradingAccountId: 'account-1',
    tradePlanId: 'plan-1',
    tradePlanVersion: 1,
    riskEvaluationId: 'risk-1',
    expiresAt: '2026-11-10T16:00:00Z',
    parameterDigest: 'digest',
    plan: {
      id: 'plan-1',
      version: 1,
      previousVersion: null,
      status: 'RISK_APPROVED',
      planningContextId: 'context-1',
      planningContextVersion: 1,
      contextCapturedAt: '2026-11-10T15:00:00Z',
      instrument: 'EUR/USD',
      direction: 'LONG',
      entryType: 'MARKET',
      entryPrice: 1.1,
      stopLoss: 1.09,
      takeProfits: [1.12],
      quantity: 1000,
      notional: 1100,
      monetaryRisk: 10,
      riskReward: 2,
      expiresAt: '2026-11-10T16:00:00Z',
      thesis: 'Test thesis',
      opportunityIds: [],
      observationIds: [],
      aiAnalysisIds: [],
      confirmationConditions: [],
      invalidationConditions: [],
      managementRules: [],
      createdAt: '2026-11-10T15:00:00Z',
      tradingAccountId: 'account-1',
    },
    risk: {
      evaluationId: 'risk-1',
      status: 'APPROVED',
      decision: 'APPROVED',
      approved: true,
      evaluatedAt: '2026-11-10T15:01:00Z',
    },
    reason: null,
  };
}
