import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { Sidebar } from './sidebar';

@Component({
  standalone: true,
  template: '',
})
class TestRoute {}

describe('Sidebar', () => {
  let component: Sidebar;
  let fixture: ComponentFixture<Sidebar>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Sidebar],
      providers: [
        provideRouter([
          { path: 'research', component: TestRoute },
          { path: 'research/experiments', component: TestRoute },
        ]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Sidebar);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should render the domain sections and all primary destinations', () => {
    const text = fixture.nativeElement.textContent;
    const links = Array.from(fixture.nativeElement.querySelectorAll('a')) as HTMLAnchorElement[];

    expect(text).toContain('Overview');
    expect(text).toContain('Market Intelligence');
    expect(text).toContain('Trading');
    expect(text).toContain('Account & Risk');
    expect(links.map((link) => link.textContent?.trim())).toEqual([
      'Dashboard',
      'Markets',
      'Economic Context',
      'Financial News',
      'Research',
      'Decision Cockpit',
      'Opportunities',
      'Positions',
      'Trade History',
      'Accounts',
      'Challenges',
    ]);
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/dashboard',
      '/markets',
      '/economic-context',
      '/financial-news',
      '/research',
      '/decision-workspace',
      '/opportunities',
      '/positions',
      '/analytics',
      '/accounts',
      '/challenges',
    ]);
  });

  it('keeps Research active for nested Research routes', async () => {
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/research/experiments');
    fixture.detectChanges();
    await fixture.whenStable();

    const research = Array.from(fixture.nativeElement.querySelectorAll('a')).find(
      (link: unknown) => (link as HTMLAnchorElement).textContent?.trim() === 'Research',
    ) as HTMLAnchorElement;

    expect(research.classList.contains('active')).toBe(true);
    expect(research.getAttribute('aria-current')).toBe('page');
  });

  it('emits navigation selection when the mobile drawer is closed', () => {
    const selection = vi.fn();
    component.navigationSelected.subscribe(selection);
    component.closeMobileNavigation();

    expect(selection).toHaveBeenCalledOnce();
  });
});
