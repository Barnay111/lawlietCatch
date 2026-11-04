import { RawEvent } from '../types';
import { clamp, round } from '../util';

/** Время на экране и переходы. Экраны из excludeScreens не попадают в события. */
export class ScreenTracker {
  private current?: { name: string; since: number };

  constructor(private readonly excluded: Set<string> = new Set()) {}

  track(name: string, t: number): RawEvent[] {
    const out = this.close(t);
    this.current = { name, since: t };
    return out;
  }

  /** Закрывает текущий экран (например, при завершении сессии). */
  close(t: number): RawEvent[] {
    if (!this.current) return [];
    const { name, since } = this.current;
    this.current = undefined;
    if (this.excluded.has(name)) return [];
    return [{
      type: 'SCREEN_VIEW',
      t: since,
      payload: { screen: name, dwellMs: round(clamp(t - since, 0, 86_400_000), 0) },
    }];
  }

  currentScreen(): string | undefined {
    return this.current?.name;
  }
}
