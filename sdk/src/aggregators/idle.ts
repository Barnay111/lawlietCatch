import { RawEvent } from '../types';
import { clamp, round } from '../util';

/** Пауза без активности, после которой фиксируется IDLE. */
export const IDLE_THRESHOLD_MS = 30_000;

/** Фиксирует паузы: при каждом взаимодействии сравнивает с предыдущим. */
export class IdleTracker {
  private lastActivity?: number;

  constructor(private readonly thresholdMs = IDLE_THRESHOLD_MS) {}

  onActivity(t: number): RawEvent[] {
    const out: RawEvent[] = [];
    if (this.lastActivity !== undefined && t - this.lastActivity >= this.thresholdMs) {
      out.push({
        type: 'IDLE',
        t: this.lastActivity,
        payload: { durationMs: round(clamp(t - this.lastActivity, 0, 86_400_000), 0) },
      });
    }
    this.lastActivity = t;
    return out;
  }

  reset(): void {
    this.lastActivity = undefined;
  }
}
