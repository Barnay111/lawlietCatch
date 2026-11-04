import { RawEvent, WireEvent } from './types';
import { clamp } from './util';

export interface RecorderOptions {
  maxEventsPerMinute: number;
}

/**
 * Единая точка входа событий. Здесь:
 * - отбрасываем всё, если согласия нет;
 * - ограничиваем частоту (скользящее окно в 1 минуту);
 * - переводим время в ISO-строку для API.
 */
export class Recorder {
  private consent = false;
  private windowStart = 0;
  private windowCount = 0;
  private droppedByLimit = 0;

  constructor(
    private readonly sink: (event: WireEvent) => void,
    private readonly opts: RecorderOptions,
    private readonly now: () => number = Date.now,
  ) {}

  setConsent(granted: boolean): void {
    this.consent = granted;
  }

  isRecording(): boolean {
    return this.consent;
  }

  get dropped(): number {
    return this.droppedByLimit;
  }

  record(events: RawEvent[]): void {
    if (!this.consent) return;
    for (const e of events) {
      if (!this.allowByRate(this.now())) {
        this.droppedByLimit += 1;
        continue;
      }
      this.sink({
        eventType: e.type,
        eventTime: new Date(clamp(e.t, 0, 8.64e15)).toISOString(),
        payload: { ...e.payload } as Record<string, unknown>,
      });
    }
  }

  private allowByRate(now: number): boolean {
    if (now - this.windowStart >= 60_000) {
      this.windowStart = now;
      this.windowCount = 0;
    }
    if (this.windowCount >= this.opts.maxEventsPerMinute) {
      return false;
    }
    this.windowCount += 1;
    return true;
  }
}
