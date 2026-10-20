import { Component, Input } from '@angular/core';

export type FreshnessIndicatorValue = 'LIVE' | 'RECENT' | 'STALE' | 'UNAVAILABLE';

@Component({
  selector: 'app-freshness-indicator',
  standalone: true,
  template: `
    <div class="freshness-indicator" [attr.data-freshness]="freshness">
      <span class="freshness-indicator__label">{{ freshness }}</span>
      @if (updatedAt) {
        <small>{{ updatedAt }}</small>
      }
    </div>
  `,
  styleUrl: './freshness-indicator.scss',
})
export class FreshnessIndicator {
  @Input({ required: true }) freshness!: FreshnessIndicatorValue;
  @Input() updatedAt: string | null = null;
}
