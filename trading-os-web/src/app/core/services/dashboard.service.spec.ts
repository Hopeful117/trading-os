import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import {
  DASHBOARD_DEFERRED_BACKOFF_MS,
  DASHBOARD_POLL_INTERVAL_MS,
  DashboardService,
} from './dashboard.service';

describe('DashboardService polling', () => {
  let service: DashboardService;
  let http: { get: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    http = { get: vi.fn() };
    TestBed.configureTestingModule({
      providers: [DashboardService, { provide: HttpClient, useValue: http }],
    });
    service = TestBed.inject(DashboardService);
  });

  it('emits the initial dashboard request and keeps the normal cadence after success', () => {
    const dashboard = { account: { accountId: 'account-a' } } as never;
    http.get.mockReturnValue(of(dashboard));
    const events: unknown[] = [];
    vi.useFakeTimers();
    const subscription = service.pollDashboard('account-a').subscribe((event) => events.push(event));

    expect(http.get).toHaveBeenCalledTimes(1);
    expect(events).toEqual([{ dashboard, error: null, deferred: false }]);

    vi.advanceTimersByTime(DASHBOARD_POLL_INTERVAL_MS);
    expect(http.get).toHaveBeenCalledTimes(2);

    subscription.unsubscribe();
    vi.useRealTimers();
  });

  it('recovers from an unrelated failure on the normal cadence', () => {
    const dashboard = { account: { accountId: 'account-a' } } as never;
    const events: unknown[] = [];
    http.get
      .mockReturnValueOnce(throwError(() => new Error('temporary failure')))
      .mockReturnValue(of(dashboard));

    vi.useFakeTimers();
    const subscription = service.pollDashboard('account-a').subscribe((event) => events.push(event));

    expect(events).toHaveLength(1);
    vi.advanceTimersByTime(DASHBOARD_POLL_INTERVAL_MS);

    expect(http.get).toHaveBeenCalledTimes(2);
    expect(events.at(-1)).toEqual({ dashboard, error: null, deferred: false });

    subscription.unsubscribe();
    vi.useRealTimers();
  });

  it('cancels the pending polling timer on unsubscribe', () => {
    http.get.mockReturnValue(of({}));

    vi.useFakeTimers();
    const subscription = service.pollDashboard('account-a').subscribe();
    subscription.unsubscribe();
    vi.advanceTimersByTime(DASHBOARD_POLL_INTERVAL_MS * 2);

    expect(http.get).toHaveBeenCalledTimes(1);
    vi.useRealTimers();
  });

  it('does not request again at the normal tick after a deferred snapshot', () => {
    http.get.mockReturnValue(
      throwError(() => ({ status: 409, error: { code: 'BROKER_SNAPSHOT_DEFERRED' } })),
    );
    vi.useFakeTimers();
    const subscription = service.pollDashboard('account-a').subscribe();
    vi.advanceTimersByTime(DASHBOARD_POLL_INTERVAL_MS);
    expect(http.get).toHaveBeenCalledTimes(1);

    subscription.unsubscribe();
    vi.useRealTimers();
  });

  it('resumes after the bounded deferred backoff', () => {
    const dashboard = { account: { accountId: 'account-a' } } as never;
    http.get
      .mockReturnValueOnce(throwError(() => ({ status: 409, error: { code: 'BROKER_SNAPSHOT_DEFERRED' } })))
      .mockReturnValue(of(dashboard));

    vi.useFakeTimers();
    const subscription = service.pollDashboard('account-a').subscribe();
    vi.advanceTimersByTime(DASHBOARD_DEFERRED_BACKOFF_MS);

    expect(http.get).toHaveBeenCalledTimes(2);
    subscription.unsubscribe();
    vi.useRealTimers();
  });

  it('does not classify an unrelated conflict as broker deferral', () => {
    http.get.mockReturnValue(throwError(() => ({ status: 409, error: { code: 'OTHER_CONFLICT' } })));
    const events: unknown[] = [];
    const subscription = service.pollDashboard('account-a').subscribe((event) => events.push(event));

    expect(events).toEqual([
      { dashboard: null, error: { status: 409, error: { code: 'OTHER_CONFLICT' } }, deferred: false },
    ]);
    subscription.unsubscribe();
  });

  it('does not carry a deferred account state into another account', () => {
    const dashboard = { account: { accountId: 'account-b' } } as never;
    http.get
      .mockReturnValueOnce(
        throwError(() => ({ status: 409, error: { code: 'BROKER_SNAPSHOT_DEFERRED' } })),
      )
      .mockReturnValue(of(dashboard));

    vi.useFakeTimers();
    const first = service.pollDashboard('account-a').subscribe();
    vi.advanceTimersByTime(DASHBOARD_POLL_INTERVAL_MS);
    expect(http.get).toHaveBeenCalledTimes(1);
    first.unsubscribe();

    const second = service.pollDashboard('account-b').subscribe();
    expect(http.get).toHaveBeenCalledTimes(2);
    second.unsubscribe();
    vi.useRealTimers();
  });
});
