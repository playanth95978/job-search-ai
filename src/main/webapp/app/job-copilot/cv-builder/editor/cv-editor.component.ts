import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import TranslateDirective from 'app/shared/language/translate.directive';

import { ResumeStore } from '../resume-store';
import { Experience, Project, SectionKey } from '../resume.model';
import { AiRewriteComponent } from '../ai-rewrite/ai-rewrite.component';

/**
 * Left panel: the editable form. Every field writes through {@link ResumeStore} so the
 * preview (which reads the same signal) updates instantly. Section order/visibility and
 * collapse are handled here; list items support add/remove/move.
 */
@Component({
  selector: 'jhi-cv-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, FaIconComponent, TranslateModule, TranslateDirective, AiRewriteComponent],
  templateUrl: './cv-editor.component.html',
  styleUrl: './cv-editor.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvEditorComponent {
  readonly store = inject(ResumeStore);
  readonly r = this.store.resume;
  readonly sectionOrder = computed(() => this.r().sectionOrder);

  private readonly collapsed = signal<ReadonlySet<string>>(new Set());

  isCollapsed(key: string): boolean {
    return this.collapsed().has(key);
  }

  toggleCollapse(key: string): void {
    const next = new Set(this.collapsed());
    if (next.has(key)) {
      next.delete(key);
    } else {
      next.add(key);
    }
    this.collapsed.set(next);
  }

  isHidden(section: SectionKey): boolean {
    return this.r().hiddenSections.includes(section);
  }

  // achievements: one per line
  achievementsText(e: Experience): string {
    return e.achievements.join('\n');
  }
  setAchievements(id: string, text: string): void {
    this.store.patchExperience(id, {
      achievements: text
        .split('\n')
        .map(x => x.trim())
        .filter(Boolean),
    });
  }

  // technologies: comma separated
  techText(p: Project): string {
    return p.technologies.join(', ');
  }
  setTech(id: string, text: string): void {
    this.store.patchProject(id, {
      technologies: text
        .split(',')
        .map(x => x.trim())
        .filter(Boolean),
    });
  }
}
