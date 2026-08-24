import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RegisterComponent } from './register';
import { RegisterService } from '../../../../core/services/register';
import { Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

describe('RegisterComponent', () => {
  let component: RegisterComponent;
  let fixture: ComponentFixture<RegisterComponent>;
  let registerService: { register: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    registerService = { register: vi.fn(() => of(undefined)) };
    router = { navigate: vi.fn(() => Promise.resolve(true)) };

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
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should navigate to /login on successful registration', () => {
    registerService.register.mockReturnValue(of(undefined));

    component.onSubmit();

    expect(registerService.register).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('should show specific message on 409 error', () => {
    registerService.register.mockReturnValue(throwError(() => ({ status: 409 })));

    component.onSubmit();

    expect(component.errorMessage).toBe('Ce nom d\u2019utilisateur ou cet email est déjà utilisé.');
  });

  it('should show specific message on 400 error', () => {
    registerService.register.mockReturnValue(throwError(() => ({ status: 400 })));

    component.onSubmit();

    expect(component.errorMessage).toBe('Les informations saisies sont invalides.');
  });

  it('should show generic message on other errors', () => {
    registerService.register.mockReturnValue(throwError(() => ({ status: 500 })));

    component.onSubmit();

    expect(component.errorMessage).toBe('Une erreur est survenue lors de la création du compte.');
  });

  it('should toggle loading state', () => {
    expect(component.loading).toBeFalsy();

    registerService.register.mockReturnValue(of(undefined));

    component.onSubmit();

    expect(component.loading).toBeFalsy();
  });

  it('should set loading to true during request', () => {
    let emitNext: () => void;
    registerService.register.mockReturnValue(
      new Observable((subscriber) => {
        emitNext = () => subscriber.next(undefined);
      }),
    );

    component.onSubmit();

    expect(component.loading).toBeTruthy();

    emitNext!();
    expect(component.loading).toBeFalsy();
  });

  it('should clear error message on new submit', () => {
    registerService.register.mockReturnValue(throwError(() => ({ status: 500 })));
    component.onSubmit();
    expect(component.errorMessage).not.toBe('');

    registerService.register.mockReturnValue(of(undefined));
    component.onSubmit();
    expect(component.errorMessage).toBe('');
  });
});
