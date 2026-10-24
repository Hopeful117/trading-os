import { Component, EventEmitter, Input, Output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

export interface NavigationItem {
  readonly label: string;
  readonly route: string;
  readonly exact: boolean;
}

export interface NavigationSection {
  readonly id: string;
  readonly label: string;
  readonly items: readonly NavigationItem[];
}

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class Sidebar {
  @Input() mobileOpen = false;
  @Output() navigationSelected = new EventEmitter<void>();

  readonly sections: readonly NavigationSection[] = [
    {
      id: 'overview',
      label: 'Overview',
      items: [{ label: 'Dashboard', route: '/dashboard', exact: false }],
    },
    {
      id: 'market-intelligence',
      label: 'Market Intelligence',
      items: [
        { label: 'Markets', route: '/markets', exact: false },
        { label: 'Economic Context', route: '/economic-context', exact: false },
        { label: 'Financial News', route: '/financial-news', exact: false },
        { label: 'Research', route: '/research', exact: false },
      ],
    },
    {
      id: 'trading',
      label: 'Trading',
      items: [
        { label: 'Decision Cockpit', route: '/decision-workspace', exact: true },
        { label: 'Opportunities', route: '/opportunities', exact: false },
        { label: 'Positions', route: '/positions', exact: false },
        { label: 'Trade History', route: '/analytics', exact: false },
      ],
    },
    {
      id: 'account-risk',
      label: 'Account & Risk',
      items: [
        { label: 'Accounts', route: '/accounts', exact: false },
        { label: 'Challenges', route: '/challenges', exact: false },
      ],
    },
  ];

  closeMobileNavigation(): void {
    this.navigationSelected.emit();
  }
}
