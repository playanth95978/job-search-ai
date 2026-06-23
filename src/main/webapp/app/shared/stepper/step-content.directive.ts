import { Directive, inject, TemplateRef } from '@angular/core';

@Directive({
  selector: '[jhiStepContent]',
})
export class JhiStepContent {
  template = inject(TemplateRef);
}
