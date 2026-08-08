import { ChangeDetectionStrategy, Component, output, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language';

/** CV upload dropzone: drag & drop or file browse. Emits the chosen {@link File}; drag state is a signal. */
@Component({
  selector: 'jhi-cv-dropzone',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FaIconComponent, TranslateModule, TranslateDirective],
  templateUrl: './cv-dropzone.component.html',
  styleUrl: './cv-dropzone.component.scss',
})
export class CvDropzoneComponent {
  readonly fileSelected = output<File>();
  readonly isDragOver = signal(false);

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver.set(true);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver.set(false);
    const file = event.dataTransfer?.files[0];
    if (file) this.fileSelected.emit(file);
  }

  onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.fileSelected.emit(file);
  }
}
