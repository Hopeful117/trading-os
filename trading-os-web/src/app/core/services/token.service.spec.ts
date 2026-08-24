// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';

import { TokenService } from './token';

describe('TokenService', () => {
  let service: TokenService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(TokenService);
  });

  afterEach(() => localStorage.clear());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('saveToken stores token in localStorage', () => {
    service.saveToken('my-jwt-token');
    expect(localStorage.getItem('jwt')).toBe('my-jwt-token');
  });

  it('getToken reads token from localStorage', () => {
    localStorage.setItem('jwt', 'stored-token');
    expect(service.getToken()).toBe('stored-token');
  });

  it('getToken returns null when no token exists', () => {
    expect(service.getToken()).toBeNull();
  });

  it('removeToken clears token from localStorage', () => {
    localStorage.setItem('jwt', 'token-to-remove');
    service.removeToken();
    expect(localStorage.getItem('jwt')).toBeNull();
  });

  it('isLoggedIn returns true when token exists', () => {
    localStorage.setItem('jwt', 'existing-token');
    expect(service.isLoggedIn()).toBe(true);
  });

  it('isLoggedIn returns false when no token exists', () => {
    expect(service.isLoggedIn()).toBe(false);
  });
});
