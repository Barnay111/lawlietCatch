import { BehaviorGuardClient } from '../src/client';
import { Recorder } from '../src/recorder';
import { Batcher } from '../src/batcher';
import { HttpTransport, TransportError, Transport } from '../src/transport';
import { assertFieldId, assertSecureUrl } from '../src/privacy';
import { WireEvent } from '../src/types';

function fakeTransport() {
  const sent: { sessionId: string; events: WireEvent[] }[] = [];
  const finished: string[] = [];
  const transport: Transport = {
    createSession: jest.fn(async () => 'sess-1'),
    sendEvents: jest.fn(async (sessionId: string, events: WireEvent[]) => { sent.push({ sessionId, events }); }),
    finishSession: jest.fn(async (id: string) => { finished.push(id); }),
  };
  return { transport, sent, finished };
}

function makeClient(t: Transport, clock: { now: number }, extra: Record<string, unknown> = {}) {
  const client = new BehaviorGuardClient({ transport: t, now: () => clock.now });
  clients.push(client);
  client.init({
    apiKey: 'bg_pk_test',
    apiUrl: 'https://api.example.com',
    consent: true,
    flushIntervalMs: 60 * 60 * 1000,
    ...extra,
  } as never);
  return client;
}

const clients: BehaviorGuardClient[] = [];
afterEach(() => {
  while (clients.length) clients.pop()!.dispose();
});

describe('privacy guards', () => {
  it('требует HTTPS', () => {
    expect(() => assertSecureUrl('http://api.example.com', false)).toThrow('HTTPS');
    expect(() => assertSecureUrl('https://api.example.com', false)).not.toThrow();
  });

  it('http допускается только для localhost с явным флагом', () => {
    expect(() => assertSecureUrl('http://localhost:8080', false)).toThrow();
    expect(() => assertSecureUrl('http://localhost:8080', true)).not.toThrow();
    expect(() => assertSecureUrl('http://evil.example.com', true)).toThrow();
  });

  it('fieldId должен быть техническим именем, не значением', () => {
    expect(() => assertFieldId('transfer.amount')).not.toThrow();
    expect(() => assertFieldId('ivan@mail.kz')).toThrow();
    expect(() => assertFieldId('4400 1234 5678 9012')).toThrow();
  });
});

describe('Recorder: согласие и лимит частоты', () => {
  it('без согласия ничего не записывается', () => {
    const sink = jest.fn();
    const r = new Recorder(sink, { maxEventsPerMinute: 10 }, () => 0);
    r.record([{ type: 'IDLE', t: 0, payload: { durationMs: 1 } }]);
    expect(sink).not.toHaveBeenCalled();
  });

  it('превышение лимита отбрасывается и считается', () => {
    const sink = jest.fn();
    let now = 0;
    const r = new Recorder(sink, { maxEventsPerMinute: 2 }, () => now);
    r.setConsent(true);
    r.record([
      { type: 'IDLE', t: 0, payload: { durationMs: 1 } },
      { type: 'IDLE', t: 0, payload: { durationMs: 2 } },
      { type: 'IDLE', t: 0, payload: { durationMs: 3 } },
    ]);
    expect(sink).toHaveBeenCalledTimes(2);
    expect(r.dropped).toBe(1);
    now = 61_000;
    r.record([{ type: 'IDLE', t: 61_000, payload: { durationMs: 4 } }]);
    expect(sink).toHaveBeenCalledTimes(3);
  });
});

describe('Batcher', () => {
  it('отправляет пакетами и не теряет события при сетевой ошибке', async () => {
    const calls: number[] = [];
    let fail = true;
    const transport: Transport = {
      createSession: async () => 's',
      finishSession: async () => undefined,
      sendEvents: async (_id, events) => {
        calls.push(events.length);
        if (fail) throw new TransportError(0, 'сеть');
      },
    };
    const b = new Batcher(transport, { maxBatchSize: 2, flushIntervalMs: 1e9, maxQueueSize: 100 });
    b.setSession('s');
    for (let i = 0; i < 3; i++) b.push({ eventType: 'IDLE', eventTime: '', payload: { durationMs: i } });
    await b.flush();
    expect(b.pending).toBe(3);
    fail = false;
    await b.flush();
    expect(b.pending).toBe(0);
    expect(b.sent).toBe(3);
  });

  it('401 отключает отправку и очищает буфер', async () => {
    const onFatal = jest.fn();
    const transport: Transport = {
      createSession: async () => 's',
      finishSession: async () => undefined,
      sendEvents: async () => { throw new TransportError(401, 'HTTP 401'); },
    };
    const b = new Batcher(transport, { maxBatchSize: 50, flushIntervalMs: 1e9, maxQueueSize: 100 });
    b.onFatal = onFatal;
    b.setSession('s');
    b.push({ eventType: 'IDLE', eventTime: '', payload: { durationMs: 1 } });
    await b.flush();
    expect(onFatal).toHaveBeenCalledWith(401);
    expect(b.pending).toBe(0);
  });
});

describe('BehaviorGuardClient', () => {
  it('без согласия startSession отказывает', async () => {
    const { transport } = fakeTransport();
    const client = makeClient(transport, { now: 0 }, { consent: false });
    await expect(client.startSession({ userId: 'client-1' })).rejects.toThrow('согласия');
  });

  it('userId с email отклоняется на клиенте', async () => {
    const { transport } = fakeTransport();
    const client = makeClient(transport, { now: 0 });
    await expect(client.startSession({ userId: 'ivan@mail.kz' })).rejects.toThrow('псевдоним');
  });

  it('полный цикл: сессия, касания, текст, экран, завершение', async () => {
    const clock = { now: 1_000 };
    const { transport, sent, finished } = fakeTransport();
    const client = makeClient(transport, clock);
    await client.startSession({ userId: 'client-7' });

    client.trackScreen('transfer');
    client.touchStart(100, 100);
    clock.now = 1_120;
    client.touchEnd();
    client.textChange('transfer.amount', 0, 1);
    clock.now = 1_300;
    client.textChange('transfer.amount', 1, 2);
    clock.now = 5_000;
    client.textBlur('transfer.amount');
    clock.now = 6_000;
    await client.endSession();

    const events = sent.flatMap((s) => s.events);
    expect(sent.every((s) => s.sessionId === 'sess-1')).toBe(true);
    const types = events.map((e) => e.eventType);
    expect(types).toEqual(expect.arrayContaining(['TAP', 'TEXT_INPUT', 'SCREEN_VIEW']));
    expect(finished).toEqual(['sess-1']);
  });

  it('текст поля никогда не попадает в отправку', async () => {
    const clock = { now: 0 };
    const { transport, sent } = fakeTransport();
    const client = makeClient(transport, clock);
    await client.startSession({ userId: 'client-8' });
    const secret = 'SECRET-4400-1234';
    client.textChange('transfer.card', 0, secret.length);
    client.textBlur('transfer.card');
    await client.endSession();
    expect(JSON.stringify(sent)).not.toContain(secret);
    expect(JSON.stringify(sent)).not.toContain('4400');
  });

  it('отзыв согласия отбрасывает буфер и закрывает сессию', async () => {
    const clock = { now: 0 };
    const { transport, sent, finished } = fakeTransport();
    const client = makeClient(transport, clock);
    await client.startSession({ userId: 'client-9' });
    client.touchStart(0, 0);
    clock.now = 100;
    client.touchEnd();
    client.setConsent(false);
    await client.endSession();
    expect(sent).toEqual([]);
    expect(finished).toEqual(['sess-1']);
    expect(client.stats().pending).toBe(0);
  });

  it('до init вызовы бросают понятную ошибку', () => {
    const c = new BehaviorGuardClient();
    expect(() => c.setConsent(true)).toThrow('init');
  });
});

describe('HttpTransport', () => {
  it('не повторяет 4xx и передаёт ключ в заголовке', async () => {
    const fetchImpl = jest.fn(async () => new Response('{}', { status: 400 }));
    const t = new HttpTransport({ apiUrl: 'https://api.example.com/', apiKey: 'bg_pk_x', fetchImpl: fetchImpl as never, backoffMs: 1 });
    await expect(t.sendEvents('s', [])).rejects.toMatchObject({ status: 400 });
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    const [url, init] = fetchImpl.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('https://api.example.com/api/v1/events/batch');
    expect((init.headers as Record<string, string>)['X-BG-Api-Key']).toBe('bg_pk_x');
  });

  it('повторяет 5xx и сетевые ошибки', async () => {
    let n = 0;
    const fetchImpl = jest.fn(async () => {
      n += 1;
      if (n < 3) return new Response('', { status: 503 });
      return new Response('{"id":"abc"}', { status: 201 });
    });
    const t = new HttpTransport({ apiUrl: 'https://api.example.com', apiKey: 'k', fetchImpl: fetchImpl as never, backoffMs: 1 });
    await expect(t.createSession('client-1', 'MOBILE', '0.1.0')).resolves.toBe('abc');
    expect(fetchImpl).toHaveBeenCalledTimes(3);
  });
});
