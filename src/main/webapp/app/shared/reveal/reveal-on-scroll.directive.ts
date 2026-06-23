import { AfterViewInit, Directive, ElementRef, inject, input, OnDestroy, Renderer2 } from '@angular/core';

export type RevealDirection = 'left' | 'right' | 'up';

/**
 * Reveals the host element when it scrolls into the viewport: it starts hidden and offset
 * (from the left, right or bottom) and animates into place once intersecting. The reveal happens
 * once. Styling lives in the global `reveal` classes (content/scss/_reveal.scss).
 *
 * Usage: `<div appReveal="left">` or `<div [appReveal]="$even ? 'left' : 'right'" [revealDelay]="i * 60">`.
 */
@Directive({
  selector: '[appReveal]',
  standalone: true,
})
export class RevealOnScrollDirective implements AfterViewInit, OnDestroy {
  /** Direction the element travels from. Empty string falls back to 'up'. */
  readonly appReveal = input<RevealDirection | ''>('up');
  /** Optional stagger delay in milliseconds. */
  readonly revealDelay = input<number>(0);

  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly renderer = inject(Renderer2);
  private observer?: IntersectionObserver;

  ngAfterViewInit(): void {
    const node = this.el.nativeElement;
    this.renderer.addClass(node, 'reveal');
    this.renderer.addClass(node, `reveal-${this.appReveal() || 'up'}`);
    if (this.revealDelay()) {
      this.renderer.setStyle(node, 'transition-delay', `${this.revealDelay()}ms`);
    }

    // No IntersectionObserver (SSR / very old browsers): show immediately.
    if (typeof IntersectionObserver === 'undefined') {
      this.renderer.addClass(node, 'reveal-visible');
      return;
    }

    this.observer = new IntersectionObserver(
      entries => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            this.renderer.addClass(node, 'reveal-visible');
            this.observer?.unobserve(node);
          }
        }
      },
      { threshold: 0.15, rootMargin: '0px 0px -10% 0px' },
    );
    this.observer.observe(node);
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }
}
