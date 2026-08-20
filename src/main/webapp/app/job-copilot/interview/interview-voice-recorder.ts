/**
 * Microphone capture for the spoken interview, wrapped so the component never touches MediaRecorder.
 *
 * One recorder per turn: `start()` asks for the mic, `stop()` resolves with the recording. The tracks are
 * always released on stop — leaving them open keeps the browser's recording indicator lit, which reads as
 * the page listening in on the candidate between questions.
 */
export class InterviewVoiceRecorder {
  /** Containers in preference order; Safari produces mp4, Chrome and Firefox webm. */
  private static readonly MIME_TYPES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg'];

  private recorder: MediaRecorder | null = null;
  private stream: MediaStream | null = null;
  private chunks: Blob[] = [];

  /** Whether this browser can record at all, checked before offering the mic. */
  static isSupported(): boolean {
    return typeof MediaRecorder !== 'undefined' && !!navigator.mediaDevices?.getUserMedia;
  }

  get recording(): boolean {
    return this.recorder?.state === 'recording';
  }

  /** Prompts for mic permission and starts capturing. Rejects if the user denies it. */
  async start(): Promise<void> {
    this.stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    this.chunks = [];
    const mimeType = InterviewVoiceRecorder.MIME_TYPES.find(type => MediaRecorder.isTypeSupported(type));
    this.recorder = new MediaRecorder(this.stream, mimeType ? { mimeType } : undefined);
    this.recorder.ondataavailable = event => {
      if (event.data.size > 0) {
        this.chunks.push(event.data);
      }
    };
    this.recorder.start();
  }

  /** Stops capturing and resolves with what was recorded, or null if nothing was. */
  async stop(): Promise<Blob | null> {
    const recorder = this.recorder;
    if (!recorder || recorder.state === 'inactive') {
      this.release();
      return null;
    }
    const type = recorder.mimeType || 'audio/webm';
    const recorded = await new Promise<Blob | null>(resolve => {
      recorder.onstop = () => resolve(this.chunks.length ? new Blob(this.chunks, { type }) : null);
      recorder.stop();
    });
    this.release();
    return recorded;
  }

  /** Drops the recording and frees the mic — used when the candidate cancels mid-answer. */
  cancel(): void {
    if (this.recorder?.state === 'recording') {
      this.recorder.stop();
    }
    this.chunks = [];
    this.release();
  }

  private release(): void {
    this.stream?.getTracks().forEach(track => track.stop());
    this.stream = null;
    this.recorder = null;
  }
}
