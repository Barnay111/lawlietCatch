import { DeviceType, WireEvent } from './types';

export class TransportError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
    this.name = 'TransportError';
  }
}

/** Операции с API. Отделено от fetch, чтобы тестировать без сети. */
export interface Transport {
  createSession(userId: string, deviceType: DeviceType, sdkVersion: string): Promise<string>;
  sendEvents(sessionId: string, events: WireEvent[]): Promise<void>;
  finishSession(sessionId: string): Promise<void>;
}

export interface HttpTransportOptions {
  apiUrl: string;
  apiKey: string;
  retries?: number;
  backoffMs?: number;
  fetchImpl?: typeof fetch;
}

/**
 * HTTP-клиент. Повторяет запрос только при сетевой ошибке и 5xx.
 * 4xx не повторяем: это ошибка данных или прав, повтор её не исправит.
 */
export class HttpTransport implements Transport {
  private readonly base: string;
  private readonly retries: number;
  private readonly backoffMs: number;
  private readonly fetchImpl: typeof fetch;

  constructor(private readonly opts: HttpTransportOptions) {
    this.base = opts.apiUrl.replace(/\/+$/, '');
    this.retries = opts.retries ?? 2;
    this.backoffMs = opts.backoffMs ?? 500;
    this.fetchImpl = opts.fetchImpl ?? fetch;
  }

  async createSession(userId: string, deviceType: DeviceType, sdkVersion: string): Promise<string> {
    const body = await this.post('/api/v1/sessions', { userId, deviceType, sdkVersion });
    return (body as { id: string }).id;
  }

  async sendEvents(sessionId: string, events: WireEvent[]): Promise<void> {
    await this.post('/api/v1/events/batch', { sessionId, events });
  }

  async finishSession(sessionId: string): Promise<void> {
    await this.post(`/api/v1/sessions/${sessionId}/finish`, undefined);
  }

  private async post(path: string, body: unknown): Promise<unknown> {
    let attempt = 0;
    for (;;) {
      let response: Response;
      try {
        response = await this.fetchImpl(this.base + path, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-BG-Api-Key': this.opts.apiKey,
          },
          body: body === undefined ? undefined : JSON.stringify(body),
        });
      } catch {
        if (attempt >= this.retries) {
          throw new TransportError(0, 'сеть недоступна');
        }
        await this.sleep(attempt++);
        continue;
      }
      if (response.ok) {
        return response.status === 204 ? undefined : response.json().catch(() => undefined);
      }
      if (response.status >= 500 && attempt < this.retries) {
        await this.sleep(attempt++);
        continue;
      }
      throw new TransportError(response.status, `HTTP ${response.status}`);
    }
  }

  private sleep(attempt: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, this.backoffMs * 2 ** attempt));
  }
}
