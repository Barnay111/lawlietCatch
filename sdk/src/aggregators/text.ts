import { RawEvent } from '../types';
import { clamp, round } from '../util';

/** Если текст вырос на 2 и больше символов без нажатия клавиши, считаем это вставкой. */
export const PASTE_MIN_CHARS = 2;

/**
 * Агрегирует набор текста в одном поле. Содержимое поля НЕ сохраняется:
 * принимаются только длины строк и время нажатий.
 * Статистика ведётся через суммы, поэтому память не растёт с числом нажатий.
 */
export class TextInputTracker {
  private keyCount = 0;
  private backspaceCount = 0;
  private pasteCount = 0;
  private firstKeyT?: number;
  private lastKeyT?: number;
  private intervalCount = 0;
  private intervalSum = 0;
  private intervalSumSq = 0;

  constructor(readonly fieldId: string) {}

  /**
   * Единственный вход. Передаются только длины текста до и после изменения.
   * Рост на 1 символ — нажатие, уменьшение — Backspace, рост на 2 и больше — вставка.
   * Так работает одинаково на iOS и Android (onKeyPress на Android ненадёжен).
   */
  onTextChange(prevLength: number, nextLength: number, t: number): void {
    const delta = nextLength - prevLength;
    if (delta === 1) {
      this.registerKey(t);
    } else if (delta < 0) {
      this.backspaceCount += 1;
    } else if (delta >= PASTE_MIN_CHARS) {
      this.pasteCount += 1;
    }
  }

  private registerKey(t: number): void {
    this.keyCount += 1;
    if (this.lastKeyT !== undefined) {
      const interval = t - this.lastKeyT;
      if (interval >= 0) {
        this.intervalCount += 1;
        this.intervalSum += interval;
        this.intervalSumSq += interval * interval;
      }
    } else {
      this.firstKeyT = t;
    }
    this.lastKeyT = t;
  }

  /** Закрывает поле (blur, отправка формы) и возвращает события. */
  flush(): RawEvent[] {
    const out: RawEvent[] = [];
    if (this.keyCount > 0) {
      const mean = this.intervalCount > 0 ? this.intervalSum / this.intervalCount : 0;
      const variance = this.intervalCount > 0
        ? Math.max(this.intervalSumSq / this.intervalCount - mean * mean, 0)
        : 0;
      const spanMs = Math.max((this.lastKeyT ?? 0) - (this.firstKeyT ?? 0), 1000);
      out.push({
        type: 'TEXT_INPUT',
        t: this.firstKeyT ?? 0,
        payload: {
          fieldId: this.fieldId,
          keyCount: clamp(this.keyCount, 0, 100_000),
          keyIntervalAvgMs: round(clamp(mean, 0, 10_000), 0),
          keyIntervalStdMs: round(clamp(Math.sqrt(variance), 0, 10_000), 0),
          charsPerMinute: round(clamp(this.keyCount / spanMs * 60_000, 0, 2_000), 0),
          backspaceCount: clamp(this.backspaceCount, 0, 100_000),
        },
      });
    }
    if (this.pasteCount > 0) {
      out.push({
        type: 'PASTE',
        t: this.lastKeyT ?? 0,
        payload: { fieldId: this.fieldId, pasteCount: clamp(this.pasteCount, 1, 1_000) },
      });
    }
    this.reset();
    return out;
  }

  private reset(): void {
    this.keyCount = 0;
    this.backspaceCount = 0;
    this.pasteCount = 0;
    this.firstKeyT = undefined;
    this.lastKeyT = undefined;
    this.intervalCount = 0;
    this.intervalSum = 0;
    this.intervalSumSq = 0;
  }
}
