import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-logo',
  standalone: true,
  template: `
    <div
      class="inline-flex items-center justify-center shrink-0 rounded-2xl bg-gradient-to-br from-green-500 to-emerald-600 shadow-sm"
      [style.width.px]="size"
      [style.height.px]="size"
    >
      <svg
        [style.width.px]="size * 0.56"
        [style.height.px]="size * 0.56"
        viewBox="0 0 24 24"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path d="M4 20c0-8 4-15 16-16 1 10-5 16-13 16H4z" fill="white" />
        <path
          d="M4 20C10 14 14 10 20 4"
          stroke="#15803d"
          stroke-width="1.4"
          stroke-linecap="round"
          opacity="0.6"
        />
      </svg>
    </div>
  `,
})
export class LogoComponent {
  @Input() size = 40;
}
