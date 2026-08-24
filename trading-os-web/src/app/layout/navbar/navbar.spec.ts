import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Navbar } from './navbar';
import { AuthService } from '../../core/services/auth.service';
import { Router, ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

describe('Navbar', () => {
  let component: Navbar;
  let fixture: ComponentFixture<Navbar>;
  let authService: { isLoggedIn: ReturnType<typeof vi.fn>; logout: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    authService = {
      isLoggedIn: vi.fn(() => false),
      logout: vi.fn(),
    };
    router = { navigate: vi.fn(() => Promise.resolve(true)) };

    await TestBed.configureTestingModule({
      imports: [Navbar],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(new Map()), snapshot: { paramMap: new Map() } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Navbar);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should show Dashboard, Accounts, Markets links when logged in', () => {
    authService.isLoggedIn.mockReturnValue(true);
    fixture.detectChanges();

    const links = fixture.nativeElement.querySelectorAll('a');
    const linkTexts = Array.from(links).map((el: unknown) => (el as Element).textContent!.trim());

    expect(linkTexts).toContain('Dashboard');
    expect(linkTexts).toContain('Accounts');
    expect(linkTexts).toContain('Markets');
    expect(linkTexts).not.toContain('Login');
    expect(linkTexts).not.toContain('Register');
  });

  it('should show Login, Register links when not logged in', () => {
    authService.isLoggedIn.mockReturnValue(false);
    fixture.detectChanges();

    const links = fixture.nativeElement.querySelectorAll('a');
    const linkTexts = Array.from(links).map((el: unknown) => (el as Element).textContent!.trim());

    expect(linkTexts).toContain('Login');
    expect(linkTexts).toContain('Register');
    expect(linkTexts).not.toContain('Dashboard');
    expect(linkTexts).not.toContain('Accounts');
    expect(linkTexts).not.toContain('Markets');
  });

  it('should call AuthService.logout and navigate to / on logout', () => {
    fixture.detectChanges();
    component.logout();

    expect(authService.logout).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });
});
