import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';
import type { CanActivateFn } from '@angular/router';

import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth.service';
import { DashboardService } from '../services/dashboard.service';
import { authGuard } from './auth.guard';
import { guestGuard } from './guest.guard';

describe('Route guards', () => {
  let authService: { isLoggedIn: ReturnType<typeof vi.fn> };
  let router: { createUrlTree: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authService = { isLoggedIn: vi.fn() };
    router = { createUrlTree: vi.fn() };

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });
  });

  describe('authGuard', () => {
    const guard = authGuard as unknown as CanActivateFn;
    it('allows navigation when the user is logged in', () => {
      authService.isLoggedIn.mockReturnValue(true);

      const result = TestBed.runInInjectionContext(() =>
        guard(undefined as never, undefined as never),
      );

      expect(result).toBe(true);
      expect(router.createUrlTree).not.toHaveBeenCalled();
    });

    it('redirects to /login when the user is not authenticated', () => {
      authService.isLoggedIn.mockReturnValue(false);
      const loginTree = {} as ReturnType<Router['createUrlTree']>;
      router.createUrlTree.mockReturnValue(loginTree);

      const result = TestBed.runInInjectionContext(() =>
        guard(undefined as never, undefined as never),
      );

      expect(result).toBe(loginTree);
      expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
    });
  });

  describe('guestGuard', () => {
    const guard = guestGuard as unknown as CanActivateFn;
    it('redirects authenticated users away from guest pages', () => {
      authService.isLoggedIn.mockReturnValue(true);
      const dashboardTree = {} as ReturnType<Router['createUrlTree']>;
      router.createUrlTree.mockReturnValue(dashboardTree);

      const result = TestBed.runInInjectionContext(() =>
        guard(undefined as never, undefined as never),
      );

      expect(result).toBe(dashboardTree);
      expect(router.createUrlTree).toHaveBeenCalledWith(['/dashboard']);
    });

    it('allows anonymous visitors on guest pages', () => {
      authService.isLoggedIn.mockReturnValue(false);

      const result = TestBed.runInInjectionContext(() =>
        guard(undefined as never, undefined as never),
      );

      expect(result).toBe(true);
      expect(router.createUrlTree).not.toHaveBeenCalled();
    });
  });
});

describe('DashboardService', () => {
  let httpMock: HttpTestingController;
  let service: DashboardService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    httpMock = TestBed.inject(HttpTestingController);
    service = TestBed.inject(DashboardService);
  });

  afterEach(() => httpMock.verify());

  it('fetches the dashboard summary for an account through the gateway', () => {
    const accountId = '11111111-2222-3333-4444-555555555555';

    service.findDashboard(accountId).subscribe();

    const request = httpMock.expectOne(
      `${environment.gatewayUrl}v1/accounts/${accountId}/dashboard`,
    );
    expect(request.request.method).toBe('GET');
    request.flush({});
  });
});
