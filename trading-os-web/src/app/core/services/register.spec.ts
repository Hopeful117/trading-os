// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { RegisterService } from './register';
import { environment } from '../../../environments/environment';

describe('RegisterService', () => {
  let service: RegisterService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    httpMock = TestBed.inject(HttpTestingController);
    service = TestBed.inject(RegisterService);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('register sends POST to correct URL with request body', () => {
    const request = {
      username: 'newuser@test.com',
      password: 'pass123',
      email: 'newuser@test.com',
    };

    service.register(request).subscribe();

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/users/register`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(null);
  });
});
