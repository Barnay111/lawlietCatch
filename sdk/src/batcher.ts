import { TransportError, Transport } from './transport';
import { WireEvent } from './types';

export interface BatcherOptions {
  maxBatchSize: number;
  flushIntervalMs: number;
  /** Верхняя граница очереди в памяти. Старые события вытесняются. */
  maxQueueSize: number;
}

/**
 * Копит события и отправляет пакетами. Очередь только в памяти:
 * при закрытии приложения несохранённые события теряются. Это осознанный выбор для MVP,
 * чтобы не писать поведенческие данные на диск устройства.
 */
export class Batcher {
  private queue: WireEvent[] = [];
  private sessionId?: string;
  private timer?: ReturnType<typeof setInterval>;
  private inFlight?: Promise<void>;
  /** Сколько событий успешно принято сервером. */
  sent = 0;
  /** Вызывается при 401/403: ключ не принят, дальнейшая отправка бессмысленна. */
  onFatal?: (status: number) => void;

  constructor(
    private readonly transport: Transport,
    private readonly opts: BatcherOptions,
  ) {}

  setSession(sessionId: string | undefined): void {
    this.sessionId = sessionId;
  }

  start(): void {
    if (this.timer === undefined) {
      this.timer = setInterval(() => void this.flush(), this.opts.flushIntervalMs);
    }
  }

  stop(): void {
    if (this.timer !== undefined) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
  }

  /** Очистка без отправки: используется при отзыве согласия. */
  clear(): void {
    this.queue = [];
  }

  push(event: WireEvent): void {
    if (this.queue.length >= this.opts.maxQueueSize) {
      this.queue.shift();
    }
    this.queue.push(event);
    if (this.queue.length >= this.opts.maxBatchSize) {
      void this.flush();
    }
  }

  get pending(): number {
    return this.queue.length;
  }

  /** Отправляет всё накопленное. Параллельные вызовы выстраиваются в очередь. */
  flush(): Promise<void> {
    this.inFlight = (this.inFlight ?? Promise.resolve()).then(() => this.drain());
    return this.inFlight;
  }

  private async drain(): Promise<void> {
    while (this.sessionId !== undefined && this.queue.length > 0) {
      const chunk = this.queue.slice(0, this.opts.maxBatchSize);
      try {
        await this.transport.sendEvents(this.sessionId, chunk);
        this.queue.splice(0, chunk.length);
        this.sent += chunk.length;
      } catch (err) {
        if (err instanceof TransportError) {
          if (err.status === 401 || err.status === 403) {
            this.clear();
            this.onFatal?.(err.status);
            return;
          }
          if (err.status >= 400 && err.status < 500) {
            // Пакет неверен (400) или сессия уже закрыта (409): повтор не поможет.
            this.queue.splice(0, chunk.length);
            continue;
          }
        }
        // Сеть недоступна: оставляем события на следующую попытку.
        return;
      }
    }
  }
}
