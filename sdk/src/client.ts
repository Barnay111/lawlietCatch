import { Batcher } from './batcher';
import { assertFieldId, assertScreenName, assertSecureUrl } from './privacy';
import { Recorder } from './recorder';
import { FormTracker } from './aggregators/form';
import { GestureTracker } from './aggregators/gesture';
import { IdleTracker } from './aggregators/idle';
import { ScreenTracker } from './aggregators/screen';
import { ScrollTracker } from './aggregators/scroll';
import { TextInputTracker } from './aggregators/text';
import { HttpTransport, Transport } from './transport';
import { DeviceType, InitOptions, RawEvent } from './types';

export const SDK_VERSION = '0.1.0';

const USER_ID_PATTERN = /^[A-Za-z0-9._:-]{1,128}$/;

export interface ClientDeps {
  transport?: Transport;
  now?: () => number;
}

export interface SdkStats {
  active: boolean;
  pending: number;
  sent: number;
  droppedByLimit: number;
  disabledByStatus?: number;
}

/**
 * Публичный API SDK. Все методы безопасно вызывать без сессии: без согласия
 * и активной сессии они ничего не делают.
 */
export class BehaviorGuardClient {
  private initialized = false;
  private sessionId?: string;
  private disabledByStatus?: number;
  private now: () => number = Date.now;
  private transport!: Transport;
  private recorder!: Recorder;
  private batcher!: Batcher;
  private screen!: ScreenTracker;
  private readonly gesture = new GestureTracker();
  private readonly scroll = new ScrollTracker();
  private readonly idle = new IdleTracker();
  private readonly forms = new FormTracker();
  private readonly texts = new Map<string, TextInputTracker>();

  constructor(private readonly deps: ClientDeps = {}) {}

  init(options: InitOptions): void {
    if (!options.apiKey) {
      throw new Error('BehaviorGuard: apiKey обязателен');
    }
    const allowLocal = options.allowInsecureLocalhost ?? false;
    assertSecureUrl(options.apiUrl, allowLocal);
    if (this.initialized) {
      this.batcher.stop();
    }

    this.now = this.deps.now ?? Date.now;
    this.transport = this.deps.transport ?? new HttpTransport({ apiUrl: options.apiUrl, apiKey: options.apiKey });
    this.batcher = new Batcher(this.transport, {
      maxBatchSize: options.maxBatchSize ?? 50,
      flushIntervalMs: options.flushIntervalMs ?? 10_000,
      maxQueueSize: 2_000,
    });
    this.batcher.onFatal = (status) => {
      this.disabledByStatus = status;
      this.stopCollection();
    };
    this.recorder = new Recorder(
      (event) => this.batcher.push(event),
      { maxEventsPerMinute: options.maxEventsPerMinute ?? 600 },
      this.now,
    );
    this.recorder.setConsent(options.consent);
    this.screen = new ScreenTracker(new Set(options.excludeScreens ?? []));
    this.batcher.start();
    this.initialized = true;
  }

  /** Отзыв согласия: останавливаем сбор, выбрасываем буфер и закрываем сессию. */
  setConsent(granted: boolean): void {
    this.ensureInit();
    this.recorder.setConsent(granted);
    if (!granted) {
      this.stopCollection();
      const id = this.sessionId;
      this.sessionId = undefined;
      this.batcher.setSession(undefined);
      if (id !== undefined) {
        this.transport.finishSession(id).catch(() => undefined);
      }
    }
  }

  async startSession(options: { userId: string; deviceType?: DeviceType }): Promise<string> {
    this.ensureInit();
    if (!this.recorder.isRecording()) {
      throw new Error('BehaviorGuard: нет согласия пользователя на сбор');
    }
    if (this.disabledByStatus !== undefined) {
      throw new Error(`BehaviorGuard: ключ отклонён сервером (HTTP ${this.disabledByStatus})`);
    }
    if (!USER_ID_PATTERN.test(options.userId)) {
      throw new Error('BehaviorGuard: userId — только псевдоним (буквы, цифры, . _ : -)');
    }
    if (this.sessionId !== undefined) {
      await this.endSession();
    }
    const id = await this.transport.createSession(options.userId, options.deviceType ?? 'MOBILE', SDK_VERSION);
    this.sessionId = id;
    this.batcher.setSession(id);
    this.idle.reset();
    return id;
  }

  /** Закрывает незавершённые агрегаты, отправляет остаток и завершает сессию. */
  async endSession(): Promise<void> {
    if (this.sessionId === undefined) return;
    const t = this.now();
    this.recorder.record([
      ...this.gesture.end(t),
      ...this.scroll.flush(),
      ...this.flushTexts(),
      ...this.screen.close(t),
    ]);
    this.gesture.cancel();
    this.forms.clear();
    await this.batcher.flush().catch(() => undefined);
    const id = this.sessionId;
    this.sessionId = undefined;
    this.batcher.setSession(undefined);
    await this.transport.finishSession(id).catch(() => undefined);
  }

  /** Экран или страница (как в примере ТЗ: trackPage("transfer")). */
  trackScreen(name: string): void {
    assertScreenName(name);
    if (!this.active()) return;
    const t = this.now();
    this.recorder.record([...this.flushTexts(), ...this.screen.track(name, t)]);
  }

  trackPage(name: string): void {
    this.trackScreen(name);
  }

  // ---- Касания: вызываются из BehaviorGuardView ----

  touchStart(x: number, y: number): void {
    if (!this.active()) return;
    const t = this.now();
    this.recorder.record(this.idle.onActivity(t));
    this.gesture.start(x, y, t);
  }

  touchMove(x: number, y: number): void {
    if (!this.active()) return;
    this.gesture.move(x, y, this.now());
  }

  touchEnd(): void {
    if (!this.active()) return;
    this.recorder.record(this.gesture.end(this.now()));
  }

  touchCancel(): void {
    this.gesture.cancel();
  }

  // ---- Прокрутка ----

  scrollOffset(x: number, y: number): void {
    if (!this.active()) return;
    const t = this.now();
    this.recorder.record(this.idle.onActivity(t));
    this.recorder.record(this.scroll.onOffset(x, y, t));
  }

  // ---- Текстовые поля: сам текст сюда не передаётся ----

  /** Передаются только длины текста до и после изменения. Сам текст не сохраняется. */
  textChange(fieldId: string, prevLength: number, nextLength: number): void {
    assertFieldId(fieldId);
    if (!this.active()) return;
    const t = this.now();
    this.recorder.record(this.idle.onActivity(t));
    this.field(fieldId).onTextChange(prevLength, nextLength, t);
  }

  textBlur(fieldId: string): void {
    assertFieldId(fieldId);
    const tracker = this.texts.get(fieldId);
    if (!tracker) return;
    this.texts.delete(fieldId);
    this.recorder.record(tracker.flush());
  }

  // ---- Формы ----

  formStart(formId: string): void {
    assertFieldId(formId);
    if (!this.active()) return;
    this.forms.start(formId, this.now());
  }

  formComplete(formId: string, fieldCount: number): void {
    assertFieldId(formId);
    if (!this.active()) return;
    this.recorder.record([...this.flushTexts(), ...this.forms.complete(formId, fieldCount, this.now())]);
  }

  /** Останавливает таймер отправки. Вызывать при выгрузке приложения или в тестах. */
  dispose(): void {
    if (this.initialized) {
      this.batcher.stop();
    }
  }

  stats(): SdkStats {
    return {
      active: this.active(),
      pending: this.initialized ? this.batcher.pending : 0,
      sent: this.initialized ? this.batcher.sent : 0,
      droppedByLimit: this.initialized ? this.recorder.dropped : 0,
      disabledByStatus: this.disabledByStatus,
    };
  }

  // ---- внутреннее ----

  private active(): boolean {
    return this.initialized && this.sessionId !== undefined && this.recorder.isRecording()
      && this.disabledByStatus === undefined;
  }

  private field(fieldId: string): TextInputTracker {
    let tracker = this.texts.get(fieldId);
    if (!tracker) {
      tracker = new TextInputTracker(fieldId);
      this.texts.set(fieldId, tracker);
    }
    return tracker;
  }

  private flushTexts(): RawEvent[] {
    const out: RawEvent[] = [];
    for (const tracker of this.texts.values()) {
      out.push(...tracker.flush());
    }
    this.texts.clear();
    return out;
  }

  /** Остановка сбора: всё накопленное отбрасывается, ничего не отправляется. */
  private stopCollection(): void {
    this.gesture.cancel();
    this.scroll.flush();
    this.texts.clear();
    this.screen.close(this.now());
    this.forms.clear();
    this.idle.reset();
    this.batcher.clear();
  }

  private ensureInit(): void {
    if (!this.initialized) {
      throw new Error('BehaviorGuard: вызовите init() до использования');
    }
  }
}

/** Синглтон для приложения: BehaviorGuard.init(...), BehaviorGuard.startSession(...). */
export const BehaviorGuard = new BehaviorGuardClient();
