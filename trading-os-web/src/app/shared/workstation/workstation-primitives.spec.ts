// @vitest-environment jsdom
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DisclosureSection } from './disclosure-section/disclosure-section';
import {
  FreshnessIndicator,
  FreshnessIndicatorValue,
} from './freshness-indicator/freshness-indicator';
import { MetricValue } from './metric-value/metric-value';
import { StatusBadge } from './status-badge/status-badge';
import { WorkspacePanelHeader } from './workspace-panel-header/workspace-panel-header';
import { WorkspacePanel } from './workspace-panel/workspace-panel';

@Component({
  standalone: true,
  imports: [
    DisclosureSection,
    FreshnessIndicator,
    MetricValue,
    StatusBadge,
    WorkspacePanel,
    WorkspacePanelHeader,
  ],
  template: `
    <app-workspace-panel labelledBy="panel-title">
      <app-workspace-panel-header>
        <span workspace-panel-title id="panel-title">Context</span>
        <span workspace-panel-meta>Next action</span>
      </app-workspace-panel-header>
      <app-metric-value label="Equity" [value]="1000" detail="USD" />
      <app-status-badge label="Tradable" tone="positive" />
      <app-freshness-indicator [freshness]="freshness" updatedAt="10:00:00" />
      <app-disclosure-section
        title="Unavailable markets"
        eyebrow="Backend decision"
        summary="2 hidden"
        [expanded]="expanded"
        (expandedChange)="expanded = $event"
      >
        <p data-testid="disclosure-content">Explicit explanation</p>
      </app-disclosure-section>
    </app-workspace-panel>
  `,
})
class PrimitiveHost {
  freshness: FreshnessIndicatorValue = 'LIVE';
  expanded = false;
}

describe('workstation presentation primitives', () => {
  let fixture: ComponentFixture<PrimitiveHost>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PrimitiveHost] }).compileComponents();
    fixture = TestBed.createComponent(PrimitiveHost);
    fixture.detectChanges();
  });

  it('composes a labelled panel with projected header content', () => {
    const panel = fixture.nativeElement.querySelector('section.workspace-panel') as HTMLElement;

    expect(panel.getAttribute('aria-labelledby')).toBe('panel-title');
    expect(panel.textContent).toContain('Context');
    expect(panel.textContent).toContain('Next action');
  });

  it('renders numeric metrics, status, and freshness as distinct presentation dimensions', () => {
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('.metric-value__number')?.textContent).toContain('1000');
    expect(element.querySelector('.status-badge')?.getAttribute('data-tone')).toBe('positive');
    expect(element.querySelector('.freshness-indicator__label')?.textContent).toContain('LIVE');
    expect(element.textContent).toContain('10:00:00');
  });

  it('keeps disclosure content collapsed until the accessible button is activated', () => {
    const element = fixture.nativeElement as HTMLElement;
    const button = element.querySelector('.disclosure-button') as HTMLButtonElement;

    expect(button.getAttribute('aria-expanded')).toBe('false');
    expect(element.querySelector('[data-testid="disclosure-content"]')).toBeNull();

    button.click();
    fixture.detectChanges();

    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(element.querySelector('[data-testid="disclosure-content"]')).toBeTruthy();
  });
});
