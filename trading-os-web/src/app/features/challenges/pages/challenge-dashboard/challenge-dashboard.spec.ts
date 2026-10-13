import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Account } from '../../../../core/models/account.model';
import {
  ChallengeDefinition,
  ChallengeInstance,
  ChallengeProgress,
} from '../../../../core/models/challenge.model';
import { AccountService } from '../../../../core/services/account.service';
import { ChallengeService } from '../../../../core/services/challenge.service';
import { ChallengeDashboard } from './challenge-dashboard';

describe('ChallengeDashboard', () => {
  let fixture: ComponentFixture<ChallengeDashboard>;
  let challengeService: {
    findCatalog: ReturnType<typeof vi.fn>;
    startChallenge: ReturnType<typeof vi.fn>;
    findProgress: ReturnType<typeof vi.fn>;
  };
  let accounts: BehaviorSubject<Account[]>;

  const accountOne = account('account-1', 'Primary');
  const accountTwo = account('account-2', 'Secondary');
  const definition = challengeDefinition();

  beforeEach(async () => {
    accounts = new BehaviorSubject<Account[]>([accountOne, accountTwo]);
    challengeService = {
      findCatalog: vi.fn(() => of([definition])),
      startChallenge: vi.fn(() => of(challengeInstance('account-1', 'ACTIVE'))),
      findProgress: vi.fn(() => of(progress('account-1', 'ACTIVE'))),
    };
    await configure({ getAccounts: () => accounts });
  });

  it('renders the active backend projection and account selector', async () => {
    await create();

    expect(text()).toContain('ACTIVE');
    expect(text()).toContain('KRAKEN_PROP');
    expect(text()).toContain('52,000');
    expect(text()).toContain('Progression vers l’objectif');
    expect(fixture.nativeElement.querySelector('select')).not.toBeNull();
  });

  it('renders the no-Challenge state only for the explicit backend code', async () => {
    challengeService.findProgress.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            error: { code: 'CHALLENGE_NOT_STARTED' },
          }),
      ),
    );
    await create();

    expect(text()).toContain('Aucun Challenge démarré');
    expect(text()).toContain('ADVANCED');
  });

  it('keeps generic progress failures out of the no-Challenge state', async () => {
    challengeService.findProgress.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            error: { detail: 'Challenge configuration is unavailable' },
          }),
      ),
    );
    await create();

    expect(text()).toContain('État Challenge indisponible');
    expect(text()).not.toContain('Aucun Challenge démarré');
  });

  it('stops polling after the explicit no-Challenge response', async () => {
    vi.useFakeTimers();
    try {
      challengeService.findProgress.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 404,
              error: { code: 'CHALLENGE_NOT_STARTED' },
            }),
        ),
      );
      fixture = TestBed.createComponent(ChallengeDashboard);
      fixture.detectChanges();
      await vi.advanceTimersByTimeAsync(0);
      fixture.detectChanges();
      await vi.advanceTimersByTimeAsync(5_000);

      expect(challengeService.findProgress).toHaveBeenCalledTimes(1);
    } finally {
      vi.useRealTimers();
    }
  });

  it('starts the selected definition for the selected account', async () => {
    await create();

    fixture.componentInstance.startSelectedChallenge();
    await nextTask();
    fixture.detectChanges();

    expect(challengeService.startChallenge).toHaveBeenCalledWith('account-1', {
      definitionId: definition.id,
      definitionVersion: definition.definitionVersion,
    });
    expect(text()).toContain('Challenge démarré');
  });

  it('renders a rejected start without hiding the error', async () => {
    challengeService.findProgress.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            error: { code: 'CHALLENGE_NOT_STARTED' },
          }),
      ),
    );
    challengeService.startChallenge.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { detail: 'Account already has an ACTIVE Challenge' },
          }),
      ),
    );
    await create();

    fixture.componentInstance.startSelectedChallenge();
    await nextTask();
    fixture.detectChanges();

    expect(text()).toContain('Account already has an ACTIVE Challenge');
  });

  it('renders PASSED and BREACHED as terminal states', async () => {
    challengeService.findProgress.mockReturnValue(of(progress('account-1', 'PASSED')));
    await create();
    expect(text()).toContain('Challenge réussi');
    expect(text()).toContain('Target reached');
    expect(text()).toContain('terminal-risk-evaluation');

    challengeService.findProgress.mockReturnValue(of(progress('account-1', 'BREACHED')));
    await reconfigure({ getAccounts: () => accounts });
    expect(text()).toContain('Challenge breached');
  });

  it('updates progress when the account changes', async () => {
    await create();

    fixture.componentInstance.selectAccount('account-2');
    await nextTask();
    fixture.detectChanges();

    expect(challengeService.findProgress).toHaveBeenCalledWith('account-2');
  });

  it('shows an actionable empty account state', async () => {
    await reconfigure({ getAccounts: () => of([]) });

    expect(text()).toContain('Aucun compte disponible');
    expect(text()).toContain('Ouvrir Accounts');
  });

  async function configure(accountProvider: { getAccounts: () => unknown }): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ChallengeDashboard],
      providers: [
        provideRouter([]),
        { provide: AccountService, useValue: accountProvider },
        { provide: ChallengeService, useValue: challengeService },
      ],
    }).compileComponents();
  }

  async function reconfigure(accountProvider: { getAccounts: () => unknown }): Promise<void> {
    await configure(accountProvider);
    await create();
  }

  async function create(): Promise<void> {
    fixture = TestBed.createComponent(ChallengeDashboard);
    fixture.detectChanges();
    await nextTask();
    fixture.detectChanges();
  }

  function nextTask(): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, 0));
  }

  function text(): string {
    return fixture.nativeElement.textContent;
  }

  function account(accountId: string, name: string): Account {
    return {
      accountId,
      name,
      baseCurrency: 'USD',
      balances: { balances: { USD: 50000 } },
      equity: 50000,
      peakEquity: 50000,
      rulesId: 'rules-1',
      userId: 'user-1',
    };
  }

  function challengeDefinition(): ChallengeDefinition {
    return {
      id: 'definition-1',
      definitionVersion: '1.0.0',
      provider: 'KRAKEN_PROP',
      product: 'EVALUATION',
      planCode: 'ADVANCED',
      capitalCurrency: 'USD',
      startingCapital: 50000,
      profitTargetRatio: 0.09,
      progressionValueSource: 'EQUITY',
      requiredSettlementModel: 'CASH',
      requiredInstrumentProductType: 'LINEAR_PERPETUAL',
      riskPolicyId: 'policy-1',
      riskPolicyVersion: '1.0.0',
      sourceUrl: 'https://example.test',
      retrievedAt: '2026-10-13T12:00:00Z',
      effectiveFrom: '2026-10-13T12:00:00Z',
      provenance: 'test',
    };
  }

  function progress(accountId: string, status: ChallengeProgress['status']): ChallengeProgress {
    return {
      challengeId: `challenge-${accountId}`,
      accountId,
      status,
      startingCapital: 50000,
      currentValue: 52000,
      targetValue: 54500,
      currentProfit: 2000,
      targetProgress: 0.4444,
      dailyReferenceBalance: null,
      dailyLossRemaining: null,
      totalDrawdownRemaining: null,
      evaluatedAt: '2026-10-13T12:00:00Z',
      passedAt: status === 'PASSED' ? '2026-10-14T12:00:00Z' : null,
      breachedAt: status === 'BREACHED' ? '2026-10-14T12:00:00Z' : null,
      terminalReason: status === 'ACTIVE' ? null : status === 'PASSED' ? 'Target reached' : 'Daily loss limit',
      terminalRiskEvaluationId: status === 'ACTIVE' ? null : 'terminal-risk-evaluation',
    };
  }

  function challengeInstance(accountId: string, status: ChallengeInstance['status']): ChallengeInstance {
    return {
      id: 'challenge-1',
      accountId,
      definitionId: definition.id,
      definitionVersion: definition.definitionVersion,
      riskPolicyId: definition.riskPolicyId,
      riskPolicyVersion: definition.riskPolicyVersion,
      startingCapital: definition.startingCapital,
      capitalCurrency: definition.capitalCurrency,
      startedAt: '2026-10-13T12:00:00Z',
      status,
      passedAt: null,
      breachedAt: null,
      terminalReason: null,
      terminalRiskEvaluationId: null,
    };
  }
});
