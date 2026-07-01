import { ChangeDetectionStrategy, Component, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'jhi-chat-container-wrapper',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="chat-container-wrapper">
      <ng-content></ng-content>
    </div>
  `,
  styleUrl: './chat-container-wrapper.component.scss',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatContainerWrapperComponent {}
