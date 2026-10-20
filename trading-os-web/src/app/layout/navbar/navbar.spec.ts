import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Navbar } from './navbar';
import { AuthService } from '../../core/services/auth.service';
import { Router, ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

describe('Navbar', () => {
  let component: Navbar;
  let fixture: ComponentFixture<Navbar>;
  let authService: {
    isLoggedIn: ReturnType<typeof vi.fn>;
    getUsername: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    authService = {
      isLoggedIn: vi.fn(() => false),
      getUsername: vi.fn(() => null),
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

  it('should keep only global links in the authenticated navbar', () => {
    authService.isLoggedIn.mockReturnValue(true);
    fixture.detectChanges();

    const links = fixture.nativeElement.querySelectorAll('a');
    const linkTexts = Array.from(links).map((el: unknown) => (el as Element).textContent!.trim());

    expect(linkTexts).toContain('Home');
    expect(linkTexts).not.toContain('Login');
    expect(linkTexts).not.toContain('Register');
    expect(linkTexts).not.toContain('Dashboard');
    expect(linkTexts).not.toContain('Accounts');
    expect(linkTexts).not.toContain('Markets');
    expect(linkTexts).not.toContain('Decision Workspace');
    expect(fixture.nativeElement.querySelector('.mobile-menu-toggle')).toBeTruthy();
  });

  it('should show Login, Register links when not logged in', () => {
    authService.isLoggedIn.mockReturnValue(false);
    fixture.detectChanges();

    const links = fixture.nativeElement.querySelectorAll('a');
    const linkTexts = Array.from(links).map((el: unknown) => (el as Element).textContent!.trim());

    expect(linkTexts).toContain('Login');
    expect(linkTexts).toContain('Register');
    expect(linkTexts).toContain('Home');
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

  it('should emit the mobile menu toggle action', () => {
    const toggle = vi.fn();
    component.menuToggle.subscribe(toggle);
    fixture.detectChanges();

    component.toggleMenu();

    expect(toggle).toHaveBeenCalledOnce();
  });
});
