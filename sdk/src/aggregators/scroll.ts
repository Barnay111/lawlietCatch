import { Direction, RawEvent } from '../types';
import { clamp, round } from '../util';

/** Пауза, которая разделяет два отдельных прокручивания. */
export const SCROLL_GAP_MS = 400;

/**
 * Агрегирует смещения ScrollView в одно прокручивание.
 * Вход — contentOffset из onScroll (координаты содержимого, не пальца).
 */
export class ScrollTracker {
  private startT?: number;
  private lastT = 0;
  private lastX = 0;
  private lastY = 0;
  private distance = 0;
  private netX = 0;
  private netY = 0;

  /** Возвращает событие предыдущего прокручивания, если текущее уже началось после паузы. */
  onOffset(x: number, y: number, t: number): RawEvent[] {
    const out: RawEvent[] = [];
    if (this.startT !== undefined && t - this.lastT > SCROLL_GAP_MS) {
      out.push(...this.flush());
    }
    if (this.startT === undefined) {
      this.startT = t;
      this.lastX = x;
      this.lastY = y;
      this.lastT = t;
      return out;
    }
    const dx = x - this.lastX;
    const dy = y - this.lastY;
    this.distance += Math.abs(dx) + Math.abs(dy);
    this.netX += dx;
    this.netY += dy;
    this.lastX = x;
    this.lastY = y;
    this.lastT = t;
    return out;
  }

  /** Закрывает текущее прокручивание. */
  flush(): RawEvent[] {
    if (this.startT === undefined || this.distance <= 0) {
      this.reset();
      return [];
    }
    const duration = Math.max(this.lastT - this.startT, 1);
    const direction: Direction = Math.abs(this.netY) >= Math.abs(this.netX)
      ? (this.netY >= 0 ? 'DOWN' : 'UP')
      : (this.netX >= 0 ? 'RIGHT' : 'LEFT');
    const event: RawEvent = {
      type: 'SCROLL',
      t: this.startT,
      payload: {
        distancePx: round(clamp(this.distance, 0, 100_000), 0),
        durationMs: round(clamp(duration, 1, 60_000), 0),
        avgSpeedPxS: round(clamp(this.distance / duration * 1000, 0, 20_000), 0),
        direction,
      },
    };
    this.reset();
    return [event];
  }

  private reset(): void {
    this.startT = undefined;
    this.distance = 0;
    this.netX = 0;
    this.netY = 0;
  }
}
