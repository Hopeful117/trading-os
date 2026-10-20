import { Component } from '@angular/core';

@Component({
  selector: 'app-workspace-panel-header',
  standalone: true,
  template: `
    <header class="workspace-panel-header">
      <div class="workspace-panel-header__title">
        <ng-content select="[workspace-panel-title]" />
      </div>
      <div class="workspace-panel-header__meta">
        <ng-content select="[workspace-panel-meta]" />
      </div>
    </header>
  `,
  styleUrl: './workspace-panel-header.scss',
})
export class WorkspacePanelHeader {}
