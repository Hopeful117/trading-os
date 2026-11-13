import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ExecutionAuthorizationResponse } from '../../../core/models/execution-authorization.model';
import { ExecutionAuthorizationService } from '../../../core/services/execution-authorization.service';
import { ExecutionAuthorizationPage } from './execution-authorization-page';

describe('ExecutionAuthorizationPage', () => {
  let fixture: ComponentFixture<ExecutionAuthorizationPage>;
  let service: {
    get: ReturnType<typeof vi.fn>;
    decide: ReturnType<typeof vi.fn>;
  };

  const routeFor = (authorizationId: string | null): ActivatedRoute => {
    const paramMap = convertToParamMap(authorizationId ? { authorizationId } : {});
    return { paramMap: of(paramMap), snapshot: { paramMap } } as unknown as ActivatedRoute;
  };

  beforeEach(async () => {
    service = {
      get: vi.fn(),
      decide: vi.fn(() => of(authorization('APPROVED'))),
    };
    await TestBed.configureTestingModule({
      imports: [ExecutionAuthorizationPage],
      providers: [
        { provide: ActivatedRoute, useValue: routeFor('authorization-1') },
        { provide: ExecutionAuthorizationService, useValue: service },
      ],
    }).compileComponents();
  });

  it('renders pending authorization and submits an approval decision', async () => {
    service.get.mockReturnValue(of(authorization('PENDING_HUMAN_CONFIRMATION')));
    fixture = TestBed.createComponent(ExecutionAuthorizationPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Confirm exact order');
    expect(fixture.nativeElement.textContent).toContain('EUR/USD');
    const password = fixture.nativeElement.querySelector('#password') as HTMLInputElement;
    password.value = 'secret';
    (fixture.nativeElement.querySelector('.approve') as HTMLButtonElement).click();

    expect(service.decide).toHaveBeenCalledWith('authorization-1', 'approve', 'secret');
  });

  it('submits rejection and ignores empty passwords', async () => {
    service.get.mockReturnValue(of(authorization('PENDING_HUMAN_CONFIRMATION')));
    fixture = TestBed.createComponent(ExecutionAuthorizationPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    fixture.componentInstance.decide(authorization('PENDING_HUMAN_CONFIRMATION'), 'reject', '   ');
    expect(service.decide).not.toHaveBeenCalled();
    fixture.componentInstance.decide(
      authorization('PENDING_HUMAN_CONFIRMATION'),
      'reject',
      'secret',
    );
    expect(service.decide).toHaveBeenCalledWith('authorization-1', 'reject', 'secret');
  });

  it('renders approved authorization with revoke action', async () => {
    service.get.mockReturnValue(of(authorization('APPROVED')));
    fixture = TestBed.createComponent(ExecutionAuthorizationPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.approve')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Revoke authorization');
    const password = fixture.nativeElement.querySelector('#password') as HTMLInputElement;
    password.value = 'secret';
    (fixture.nativeElement.querySelector('.reject') as HTMLButtonElement).click();
    expect(service.decide).toHaveBeenCalledWith('authorization-1', 'revoke', 'secret');
  });

  it('renders service errors and missing route identifiers', async () => {
    service.get.mockReturnValue(throwError(() => ({ error: { message: 'expired' } })));
    fixture = TestBed.createComponent(ExecutionAuthorizationPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('expired');

    vi.clearAllMocks();
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ExecutionAuthorizationPage],
      providers: [
        { provide: ActivatedRoute, useValue: routeFor(null) },
        { provide: ExecutionAuthorizationService, useValue: service },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ExecutionAuthorizationPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(service.get).not.toHaveBeenCalled();
    fixture.componentInstance.decide(authorization('APPROVED'), 'revoke', 'secret');
    expect(service.decide).not.toHaveBeenCalled();
  });
});

function authorization(
  status: ExecutionAuthorizationResponse['status'],
): ExecutionAuthorizationResponse {
  return {
    authorizationId: 'authorization-1',
    status,
    ownerUserId: 'user-1',
    agentId: 'agent-1',
    brokerAccountId: 'broker-1',
    tradingAccountId: 'account-1',
    tradePlanId: 'plan-1',
    tradePlanVersion: 1,
    riskEvaluationId: 'risk-1',
    canonicalMarketId: 'market-1',
    requestFingerprint: 'fingerprint',
    confirmationUrl: 'https://example.test/confirmation',
    expiresAt: '2026-11-13T16:00:00Z',
    executionIntentId: null,
    plan: {
      instrument: 'EUR/USD',
      instrumentProvider: 'CTRADER',
      instrumentProductType: 'CFD',
      entryType: 'MARKET',
      direction: 'LONG',
      quantity: 1000,
      entryPrice: 1.1,
      stopLoss: 1.09,
      takeProfits: [1.12],
      monetaryRisk: 10,
      thesis: 'Test thesis',
    },
  };
}
