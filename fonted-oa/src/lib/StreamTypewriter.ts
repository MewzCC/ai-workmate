type TextConsumer = (text: string) => void;

const FRAME_INTERVAL_MS = 18;

export class StreamTypewriter {
  private readonly queue: string[] = [];
  private timer: ReturnType<typeof setTimeout> | null = null;
  private finishing = false;
  private resolved = false;
  private resolveFinished: (() => void) | null = null;
  private readonly finished = new Promise<void>((resolve) => {
    this.resolveFinished = resolve;
  });

  constructor(private readonly consume: TextConsumer) {}

  push(text: string): void {
    if (!text || this.resolved) return;
    if (prefersReducedMotion()) {
      this.consume(text);
      return;
    }
    this.queue.push(...Array.from(text));
    this.schedule();
  }

  async finish(): Promise<void> {
    this.finishing = true;
    if (!this.queue.length) this.resolve();
    return this.finished;
  }

  cancel(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    this.queue.length = 0;
    this.resolve();
  }

  private schedule(): void {
    if (this.timer || this.resolved) return;
    this.timer = setTimeout(() => this.tick(), FRAME_INTERVAL_MS);
  }

  private tick(): void {
    this.timer = null;
    const batchSize = this.resolveBatchSize();
    const text = this.queue.splice(0, batchSize).join('');
    if (text) this.consume(text);
    if (this.queue.length) {
      this.schedule();
    } else if (this.finishing) {
      this.resolve();
    }
  }

  private resolveBatchSize(): number {
    if (this.queue.length > 600) return 8;
    if (this.queue.length > 240) return 5;
    if (this.queue.length > 80) return 3;
    return 1;
  }

  private resolve(): void {
    if (this.resolved) return;
    this.resolved = true;
    this.resolveFinished?.();
  }
}

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}
