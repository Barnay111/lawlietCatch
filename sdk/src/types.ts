/**
 * Типы событий. Список и поля совпадают с серверным allowlist
 * (backend/.../events/EventType.java). Любое поле вне этих типов сервер отклонит.
 */

export type Direction = 'UP' | 'DOWN' | 'LEFT' | 'RIGHT';

export interface EventPayloads {
  TAP: { durationMs: number; intervalMs?: number };
  SWIPE: { distancePx: number; durationMs: number; avgSpeedPxS: number; direction: Direction };
  TOUCH_TRAJECTORY: { pointCount: number; straightness: number; avgSpeedPxS: number };
  SCROLL: { distancePx: number; durationMs: number; avgSpeedPxS: number; direction: Direction };
  TEXT_INPUT: {
    fieldId: string;
    keyCount: number;
    keyIntervalAvgMs: number;
    keyIntervalStdMs: number;
    charsPerMinute: number;
    backspaceCount: number;
  };
  PASTE: { fieldId: string; pasteCount: number };
  SCREEN_VIEW: { screen: string; dwellMs: number };
  IDLE: { durationMs: number };
  FORM_COMPLETION: { formId: string; durationMs: number; fieldCount: number };
}

export type EventType = keyof EventPayloads;

/** Внутреннее событие: время в миллисекундах, до отправки. */
export interface RawEvent<T extends EventType = EventType> {
  type: T;
  payload: EventPayloads[T];
  t: number;
}

/** Событие в формате API. */
export interface WireEvent {
  eventType: EventType;
  eventTime: string;
  payload: Record<string, unknown>;
}

export type DeviceType = 'MOBILE' | 'TABLET';

export interface InitOptions {
  /** Публичный ключ интеграции (не секрет). */
  apiKey: string;
  /** Базовый адрес API. Только HTTPS, кроме localhost при allowInsecureLocalhost. */
  apiUrl: string;
  /** Явное согласие пользователя на сбор. Без него сбор не начинается. */
  consent: boolean;
  /** Экраны, на которых сбор отключён (например, экран ввода PIN). */
  excludeScreens?: string[];
  /** Лимит событий в минуту на сессию. По умолчанию 600. */
  maxEventsPerMinute?: number;
  /** Интервал отправки пакетов, мс. По умолчанию 10 000. */
  flushIntervalMs?: number;
  /** Размер пакета. Сервер принимает до 200. По умолчанию 50. */
  maxBatchSize?: number;
  /** Разрешить http://localhost (только для разработки). */
  allowInsecureLocalhost?: boolean;
  /** Версия SDK, передаётся в сессию. */
  sdkVersion?: string;
}
