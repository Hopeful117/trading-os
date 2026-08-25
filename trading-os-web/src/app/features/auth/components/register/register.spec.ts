import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RegisterComponent } from './register';
import { RegisterService } from '../../../../core/services/register';
import { Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

describe('RegisterComponent', () => {
  let component: RegisterComponent;
  let fixture: ComponentFixture<RegisterComponent>;
  let registerService: { register: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  async function setup() {
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        { provide: RegisterService, useValue: registerService },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  }

  beforeEach(() => {
    registerService = { register: vi.fn(() => of('User successfully created')) };
    router = { navigate: vi.fn(() => Promise.resolve(true)) };
  });

  it('should create', async () => {
    await setup();
    expect(component).toBeTruthy();
  });

  it('navigates to /login carrying accountCreated state on success', async () => {
    await setup();

    component.onSubmit();

    expect(registerService.register).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      state: { accountCreated: true, username: '' },
    });
    expect(component.loading()).toBe(false);
  });

  it('shows visible error in DOM on duplicate account (409) and stays on form', async () => {
    registerService.register.mockReturnValue(throwError(() => ({ status: 409 })));
    await setup();

    component.onSubmit();
    await fixture.whenStable();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector(
      '[data-testid="register-error"]',
    ) as HTMLElement | null;
    expect(error).not.toBeNull();
    expect(error!.textContent).toContain('déjà utilisé');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('shows generic visible error for unexpected failures', async () => {
    registerService.register.mockReturnValue(throwError(() => ({ status: 0 })));
    await setup();

    component.onSubmit();
    await fixture.whenStable();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector(
      '[data-testid="register-error"]',
    ) as HTMLElement | null;
    expect(error).not.toBeNull();
    expect(error!.textContent).toContain('Une erreur est survenue');
  });

  it('shows validation message for invalid payload (400)', async () => {
    registerService.register.mockReturnValue(throwError(() => ({ status: 400 })));
    await setup();

    component.onSubmit();
    await fixture.whenStable();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector(
      '[data-testid="register-error"]',
    ) as HTMLElement | null;
    expect(error!.textContent).toContain('invalides');
  });

  it('disables submit button while request is in flight and blocks double submit', async () => {
    registerService.register.mockReturnValue(new Subject<void>());
    await setup();

    component.onSubmit();
    await fixture.whenStable();
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector(
      '[data-testid="register-submit"]',
    ) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain('Création…');

    component.onSubmit();
    expect(registerService.register).toHaveBeenCalledTimes(1);
  });
});
