import { Component, Input } from '@angular/core';

export type StatusBadgeTone = 'positive' | 'warning' | 'neutral' | 'negative';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `<span class="status-badge" [attr.data-tone]="tone">{{ label }}</span>`,
  styleUrl: './status-badge.scss',
})
export class StatusBadge {
  @Input({ required: true }) label!: string;
  @Input() tone: StatusBadgeTone = 'neutral';
}
