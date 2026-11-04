import { RawEvent } from '../types';
import { clamp, round } from '../util';

/** Время заполнения формы: от старта до отправки. Значения полей не передаются. */
export class FormTracker {
  private starts = new Map<string, number>();

  start(formId: string, t: number): void {
    if (!this.starts.has(formId)) {
      this.starts.set(formId, t);
    }
  }

  complete(formId: string, fieldCount: number, t: number): RawEvent[] {
    const started = this.starts.get(formId);
    this.starts.delete(formId);
    if (started === undefined) return [];
    return [{
      type: 'FORM_COMPLETION',
      t: started,
      payload: {
        formId,
        durationMs: round(clamp(t - started, 0, 86_400_000), 0),
        fieldCount: clamp(Math.round(fieldCount), 1, 500),
      },
    }];
  }

  clear(): void {
    this.starts.clear();
  }
}
