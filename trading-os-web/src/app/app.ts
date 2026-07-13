import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Shell } from './layout/shell/shell';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Shell],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly title = signal('trading-os-web');
}
