import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

import { JobCopilotService } from '../shared/job-copilot.service';
import { RadarHit, jobSourceColor, jobSourceLabel } from '../shared/job-copilot.model';
import { ApplyModalComponent } from '../shared/apply-modal/apply-modal.component';

@Component({
  selector: 'jhi-job-copilot-radar',
  standalone: true,
  imports: [CommonModule, FaIconComponent, TranslateModule],
  templateUrl: './radar.component.html',
  styleUrl: './radar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RadarComponent implements OnInit {
  hits = signal<RadarHit[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  private service = inject(JobCopilotService);
  private modalService = inject(NgbModal);

  ngOnInit(): void {
    this.service.getRadar().subscribe({
      next: h => {
        this.hits.set(h);
        this.loading.set(false);
        // Opening the radar marks its unseen items as read.
        h.filter(x => !x.seen).forEach(x => this.service.markRadarSeen(x.id).subscribe({ error() {} }));
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Failed to load radar');
        this.loading.set(false);
      },
    });
  }

  dismiss(hit: RadarHit, event: Event): void {
    event.stopPropagation();
    this.hits.update(list => list.filter(h => h.id !== hit.id));
    this.service.dismissRadar(hit.id).subscribe({ error() {} });
  }

  apply(hit: RadarHit): void {
    const modalRef = this.modalService.open(ApplyModalComponent, { size: 'xl', backdrop: 'static', scrollable: true });
    modalRef.componentInstance.jobOfferId.set(hit.jobOffer.id);
    modalRef.componentInstance.jobOffer.set(hit.jobOffer);
  }

  scorePct(score: number): number {
    return Math.round(score * 100);
  }

  sourceLabel(source?: string): string {
    return jobSourceLabel(source);
  }

  sourceColor(source?: string): string {
    return jobSourceColor(source);
  }
}
