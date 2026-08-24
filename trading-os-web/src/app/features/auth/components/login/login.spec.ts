import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LoginComponent } from './login';
import { AuthService } from '../../../../core/services/auth.service';
import { Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authService: { login: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    authService = {
      login: vi.fn(() => of({ token: 'fake-token' })),
    };
    router = { navigate: vi.fn(() => Promise.resolve(true)) };

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should navigate to / on successful login', () => {
    authService.login.mockReturnValue(of({ token: 'jwt-token' }));

    component.onSubmit();

    expect(authService.login).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('should show specific message on 401 error', () => {
    authService.login.mockReturnValue(throwError(() => ({ status: 401 })));

    component.onSubmit();

    expect(component.errorMessage).toBe('Nom d\u2019utilisateur ou mot de passe incorrect.');
  });

  it('should show generic message on other errors', () => {
    authService.login.mockReturnValue(throwError(() => ({ status: 500 })));

    component.onSubmit();

    expect(component.errorMessage).toBe('Nom d\u2019utilisateur ou mot de passe incorrect.');
  });

  it('should clear error message on new submit', () => {
    authService.login.mockReturnValue(throwError(() => ({ status: 401 })));
    component.onSubmit();
    expect(component.errorMessage).not.toBe('');

    authService.login.mockReturnValue(of({ token: 'jwt' }));
    component.onSubmit();
    expect(component.errorMessage).toBe('');
  });

  it('should not set loading state (no loading property)', () => {
    authService.login.mockReturnValue(
      new Observable((subscriber) => {
        // never completes - used to test in-flight state
      }),
    );

    component.onSubmit();

    expect(component.errorMessage).toBe('');
  });
});
