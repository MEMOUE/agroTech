import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AssistantWidgetComponent } from './shared/assistant-widget/assistant-widget.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AssistantWidgetComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('agroTecheFrontend');
}
