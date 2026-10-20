import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Shell } from './shell';
import { provideRouter } from '@angular/router';

describe('Shell', () => {
  let component: Shell;
  let fixture: ComponentFixture<Shell>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Shell],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Shell);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should toggle and close the mobile navigation state', () => {
    expect(component.mobileMenuOpen).toBe(false);

    component.toggleMobileMenu();
    expect(component.mobileMenuOpen).toBe(true);

    component.closeMobileMenu();
    expect(component.mobileMenuOpen).toBe(false);
  });
});
