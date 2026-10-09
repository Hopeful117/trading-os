import { ComponentFixture, TestBed } from '@angular/core/testing';

import { HomeComponent } from './home';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { vi } from 'vitest';

describe('Home', () => {
  let component: HomeComponent;
  let fixture: ComponentFixture<HomeComponent>;
  let authService: { isLoggedIn: ReturnType<typeof vi.fn>; getUsername: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    authService = { isLoggedIn: vi.fn(() => false), getUsername: vi.fn(() => null) };
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: authService }],
    }).compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows authentication actions when logged out', () => {
    expect(fixture.nativeElement.textContent).toContain('Se connecter');
    expect(fixture.nativeElement.textContent).toContain('Créer un compte');
  });

  it('shows a welcome message with the username when logged in', async () => {
    fixture.destroy();
    authService.isLoggedIn.mockReturnValue(true);
    authService.getUsername.mockReturnValue('alice');
    fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Bienvenue alice.');
    expect(fixture.nativeElement.textContent).not.toContain('Se connecter');
    expect(fixture.nativeElement.textContent).not.toContain('Créer un compte');
  });
});
