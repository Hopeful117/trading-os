// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { Router } from '@angular/router';
import { vi } from 'vitest';

import { authInterceptor } from './auth-interceptor';
import { TokenService } from '../services/token';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let tokenService: { getToken: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    tokenService = { getToken: vi.fn() };
    router = { navigate: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: TokenService, useValue: tokenService },
        { provide: Router, useValue: router },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(authInterceptor).toBeTruthy();
  });

  it('attaches Bearer token when token is present', () => {
    tokenService.getToken.mockReturnValue('test-jwt-token');

    httpClient.get('/api/v1/test').subscribe();

    const req = httpMock.expectOne('/api/v1/test');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-jwt-token');
    req.flush({});
  });

  it('does not attach auth header when token is absent', () => {
    tokenService.getToken.mockReturnValue(null);

    httpClient.get('/api/v1/test').subscribe();

    const req = httpMock.expectOne('/api/v1/test');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('skips auth header for login URL', () => {
    tokenService.getToken.mockReturnValue('test-jwt-token');

    httpClient.post('/api/v1/users/login', {}).subscribe();

    const req = httpMock.expectOne('/api/v1/users/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({ token: 'new-token' });
  });

  it('skips auth header for register URL', () => {
    tokenService.getToken.mockReturnValue('test-jwt-token');

    httpClient.post('/api/v1/users/register', {}).subscribe();

    const req = httpMock.expectOne('/api/v1/users/register');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('navigates to /error?status=401 on 401 response', () => {
    tokenService.getToken.mockReturnValue('test-jwt-token');

    httpClient.get('/api/v1/test').subscribe({
      error: (err: HttpErrorResponse) => expect(err.status).toBe(401),
    });

    const req = httpMock.expectOne('/api/v1/test');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(router.navigate).toHaveBeenCalledWith(['/error'], {
      queryParams: { status: 401 },
    });
  });

  it('navigates to /error?status=403 on 403 response', () => {
    tokenService.getToken.mockReturnValue('test-jwt-token');

    httpClient.get('/api/v1/test').subscribe({
      error: (err: HttpErrorResponse) => expect(err.status).toBe(403),
    });

    const req = httpMock.expectOne('/api/v1/test');
    req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });

    expect(router.navigate).toHaveBeenCalledWith(['/error'], {
      queryParams: { status: 403 },
    });
  });

  it('re-throws 500 error without redirect', () => {
    tokenService.getToken.mockReturnValue('test-jwt-token');

    httpClient.get('/api/v1/test').subscribe({
      error: (err: HttpErrorResponse) => expect(err.status).toBe(500),
    });

    const req = httpMock.expectOne('/api/v1/test');
    req.flush('Server Error', { status: 500, statusText: 'Internal Server Error' });

    expect(router.navigate).not.toHaveBeenCalled();
  });
});
