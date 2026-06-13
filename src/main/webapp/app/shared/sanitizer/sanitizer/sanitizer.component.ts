import { Component, computed, inject, model } from '@angular/core';
import { SanitizerService } from '../sanitizer.service';

@Component({
  selector: 'jhi-sanitizer',
  imports: [],
  templateUrl: './sanitizer.component.html',
  styleUrl: './sanitizer.component.scss',
})
export class SanitizerComponent {
  html = model<string>();
  protected sanitizerService: SanitizerService = inject(SanitizerService);

  protected thtml = computed(() => {
    const trustHtml = this.html();
    if (trustHtml) {
      return this.sanitizerService.sanitizeHtml(trustHtml);
    }
    return trustHtml;
  });
}
