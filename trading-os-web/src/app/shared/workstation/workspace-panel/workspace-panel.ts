import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-workspace-panel',
  standalone: true,
  template: `
    <section class="workspace-panel" [attr.aria-labelledby]="labelledBy">
      <ng-content />
    </section>
  `,
  styleUrl: './workspace-panel.scss',
})
export class WorkspacePanel {
  @Input() labelledBy: string | null = null;
}
