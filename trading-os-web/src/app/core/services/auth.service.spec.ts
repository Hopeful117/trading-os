// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { vi } from 'vitest';

import { AuthService } from './auth.service';
import { TokenService } from './token';
import { environment } from '../../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let tokenService: {
    saveToken: ReturnType<typeof vi.fn>;
    removeToken: ReturnType<typeof vi.fn>;
    isLoggedIn: ReturnType<typeof vi.fn>;
    getToken: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    tokenService = {
      saveToken: vi.fn(),
      removeToken: vi.fn(),
      isLoggedIn: vi.fn(),
      getToken: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AuthService, { provide: TokenService, useValue: tokenService }],
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('login sends POST to correct URL with correct body and saves token', () => {
    const loginRequest = { username: 'user@test.com', password: 'pass123' };
    const authResponse = { token: 'jwt-token-abc' };

    service.login(loginRequest).subscribe((response) => {
      expect(response).toEqual(authResponse);
      expect(tokenService.saveToken).toHaveBeenCalledWith('jwt-token-abc');
    });

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/users/login`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(loginRequest);
    req.flush(authResponse);
  });

  it('logout removes token', () => {
    service.logout();
    expect(tokenService.removeToken).toHaveBeenCalled();
  });

  it('isLoggedIn delegates to TokenService', () => {
    tokenService.isLoggedIn.mockReturnValue(true);
    expect(service.isLoggedIn()).toBe(true);

    tokenService.isLoggedIn.mockReturnValue(false);
    expect(service.isLoggedIn()).toBe(false);
  });

  it('getToken delegates to TokenService', () => {
    tokenService.getToken.mockReturnValue('my-token');
    expect(service.getToken()).toBe('my-token');

    tokenService.getToken.mockReturnValue(null);
    expect(service.getToken()).toBeNull();
  });
});
