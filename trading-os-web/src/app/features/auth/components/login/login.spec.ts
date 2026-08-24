import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LoginComponent } from './login';
import { AuthService } from '../../../../core/services/auth.service';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authService: { login: ReturnType<typeof vi.fn> };
  let router: {
    navigate: ReturnType<typeof vi.fn>;
    getCurrentNavigation: ReturnType<typeof vi.fn>;
  };

  async function setup() {
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
  }

  beforeEach(() => {
    authService = {
      login: vi.fn(() => of({ token: 'fake-token' })),
    };
    router = {
      navigate: vi.fn(() => Promise.resolve(true)),
      getCurrentNavigation: vi.fn(() => null),
    };
  });

  it('should create without account-created banner by default', async () => {
    await setup();
    expect(component).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="account-created-banner"]'),
    ).toBeNull();
  });

  it('shows visible success banner when arriving from successful registration', async () => {
    router.getCurrentNavigation.mockReturnValue({
      extras: { state: { accountCreated: true, username: 'newuser' } },
    });
    await setup();

    const banner = fixture.nativeElement.querySelector(
      '[data-testid="account-created-banner"]',
    ) as HTMLElement | null;
    expect(banner).not.toBeNull();
    expect(banner!.textContent).toContain('Compte créé avec succès');
  });

  it('navigates to home on successful login', async () => {
    await setup();

    component.onSubmit();

    expect(authService.login).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('shows visible error in DOM on failed login', async () => {
    authService.login.mockReturnValue(throwError(() => ({ status: 401 })));
    await setup();

    component.onSubmit();
    await fixture.whenStable();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector(
      '[data-testid="login-error"]',
    ) as HTMLElement | null;
    expect(error).not.toBeNull();
    expect(error!.textContent).toContain('incorrect');
  });

  it('clears the success banner when a new login attempt starts', async () => {
    authService.login.mockReturnValue(throwError(() => ({ status: 401 })));
    router.getCurrentNavigation.mockReturnValue({
      extras: { state: { accountCreated: true } },
    });
    await setup();

    component.onSubmit();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="account-created-banner"]'),
    ).toBeNull();
  });
});
