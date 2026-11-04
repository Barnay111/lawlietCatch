import { RawEvent } from '../types';
import { clamp, round } from '../util';

/** Смещение, начиная с которого жест считается свайпом, а не касанием. */
export const SWIPE_MIN_PX = 30;

interface Point { x: number; y: number; t: number }

/**
 * Одно касание (один палец). Жест с несколькими пальцами (масштабирование) не анализируем:
 * вызывающий код вызывает cancel().
 */
export class GestureTracker {
  private points: Point[] = [];
  private pathLength = 0;
  private lastTapEnd?: number;
  private active = false;

  start(x: number, y: number, t: number): void {
    this.points = [{ x, y, t }];
    this.pathLength = 0;
    this.active = true;
  }

  move(x: number, y: number, t: number): void {
    if (!this.active) return;
    const last = this.points[this.points.length - 1];
    this.pathLength += Math.hypot(x - last.x, y - last.y);
    this.points.push({ x, y, t });
  }

  cancel(): void {
    this.points = [];
    this.pathLength = 0;
    this.active = false;
  }

  /** Завершает жест и возвращает события: TAP или SWIPE, плюс TOUCH_TRAJECTORY при движении. */
  end(t: number): RawEvent[] {
    if (!this.active || this.points.length === 0) {
      this.cancel();
      return [];
    }
    const first = this.points[0];
    const last = this.points[this.points.length - 1];
    const duration = Math.max(t - first.t, 0);
    const displacement = Math.hypot(last.x - first.x, last.y - first.y);
    const out: RawEvent[] = [];

    if (this.pathLength < SWIPE_MIN_PX) {
      out.push({
        type: 'TAP',
        t: first.t,
        payload: {
          durationMs: round(clamp(duration, 10, 10_000), 0),
          ...(this.lastTapEnd !== undefined && first.t >= this.lastTapEnd
            ? { intervalMs: round(clamp(first.t - this.lastTapEnd, 0, 600_000), 0) }
            : {}),
        },
      });
      this.lastTapEnd = t;
    } else {
      const speed = this.pathLength / Math.max(duration, 1) * 1000;
      out.push({
        type: 'SWIPE',
        t: first.t,
        payload: {
          distancePx: round(clamp(displacement, 0, 20_000), 0),
          durationMs: round(clamp(duration, 1, 10_000), 0),
          avgSpeedPxS: round(clamp(speed, 0, 20_000), 0),
          direction: dominantDirection(last.x - first.x, last.y - first.y),
        },
      });
      if (this.points.length >= 3 && this.pathLength > 0) {
        out.push({
          type: 'TOUCH_TRAJECTORY',
          t: first.t,
          payload: {
            pointCount: clamp(this.points.length, 1, 100_000),
            straightness: round(clamp(displacement / this.pathLength, 0, 1), 3),
            avgSpeedPxS: round(clamp(speed, 0, 20_000), 0),
          },
        });
      }
    }
    this.cancel();
    return out;
  }
}

/** Направление в экранных координатах: y растёт вниз. */
export function dominantDirection(dx: number, dy: number): 'UP' | 'DOWN' | 'LEFT' | 'RIGHT' {
  if (Math.abs(dx) >= Math.abs(dy)) {
    return dx >= 0 ? 'RIGHT' : 'LEFT';
  }
  return dy >= 0 ? 'DOWN' : 'UP';
}
