/**
 * Контракт SDK ↔ backend: события, которые SDK сформировал из реальных жестов,
 * сервер должен принять без ошибок. Запускается только при наличии работающего backend:
 *   BG_E2E_URL=http://localhost:8080 BG_E2E_KEY=bg_pk_... npx jest contract
 */
import { BehaviorGuardClient } from '../src/client';
import { HttpTransport } from '../src/transport';

const url = process.env.BG_E2E_URL;
const key = process.env.BG_E2E_KEY;
const maybe = url && key ? describe : describe.skip;

maybe('SDK -> backend contract', () => {
  it('реальный сценарий: экран, касания, свайп, текст, вставка, прокрутка, форма, завершение', async () => {
    let now = 1_700_000_000_000;
    const transport = new HttpTransport({ apiUrl: url!, apiKey: key! });
    const client = new BehaviorGuardClient({ transport, now: () => now });
    client.init({ apiKey: key!, apiUrl: url!, consent: true, allowInsecureLocalhost: true, flushIntervalMs: 1e9 });

    try {
      await client.startSession({ userId: `e2e-${Date.now()}`, deviceType: 'MOBILE' });
      client.trackScreen('home');

      client.touchStart(200, 400); now += 90; client.touchEnd();          // TAP
      now += 500;
      client.touchStart(300, 500); client.touchMove(260, 500); now += 40;
      client.touchMove(180, 505); now += 60; client.touchMove(90, 510); now += 40;
      client.touchEnd();                                                    // SWIPE + TRAJECTORY

      client.scrollOffset(0, 0); now += 80; client.scrollOffset(0, 300); now += 80; client.scrollOffset(0, 600);
      now += 1_000; client.scrollOffset(0, 610);                             // SCROLL закрывается паузой

      client.trackScreen('transfer');
      client.formStart('transfer');
      client.textChange('transfer.amount', 0, 1); now += 200;
      client.textChange('transfer.amount', 1, 2); now += 180;
      client.textChange('transfer.amount', 2, 1); now += 300;             // Backspace
      client.textChange('transfer.amount', 1, 4); now += 400;             // вставка
      client.textBlur('transfer.amount');
      client.formComplete('transfer', 3);

      now += 35_000;
      client.touchStart(10, 10);                                            // IDLE при возврате

      await client.endSession();
      const stats = client.stats();
      expect(stats.sent).toBeGreaterThanOrEqual(7);
      expect(stats.pending).toBe(0);
    } finally {
      client.dispose();
    }
  }, 30_000);

  it('сервер отклоняет поле, которого нет в allowlist (защита от подмены SDK)', async () => {
    const res = await fetch(url! + '/api/v1/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-BG-Api-Key': key! },
      body: JSON.stringify({ userId: `e2e-x-${Date.now()}`, deviceType: 'MOBILE', sdkVersion: '0.1.0' }),
    });
    const { id } = (await res.json()) as { id: string };
    const bad = await fetch(url! + '/api/v1/events/batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-BG-Api-Key': key! },
      body: JSON.stringify({
        sessionId: id,
        events: [{ eventType: 'TEXT_INPUT', eventTime: new Date().toISOString(),
          payload: { fieldId: 'x.y', keyCount: 1, keyIntervalAvgMs: 0, keyIntervalStdMs: 0,
            charsPerMinute: 60, backspaceCount: 0, value: 'секрет' } }],
      }),
    });
    expect(bad.status).toBe(400);
  });
});
