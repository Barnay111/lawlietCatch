import { GestureTracker } from '../src/aggregators/gesture';
import { ScrollTracker } from '../src/aggregators/scroll';
import { TextInputTracker } from '../src/aggregators/text';
import { IdleTracker } from '../src/aggregators/idle';
import { ScreenTracker } from '../src/aggregators/screen';
import { FormTracker } from '../src/aggregators/form';

describe('GestureTracker', () => {
  it('короткое касание даёт TAP с длительностью', () => {
    const g = new GestureTracker();
    g.start(100, 100, 1000);
    const [tap] = g.end(1120);
    expect(tap.type).toBe('TAP');
    expect(tap.payload).toEqual({ durationMs: 120 });
  });

  it('второе касание получает интервал от предыдущего', () => {
    const g = new GestureTracker();
    g.start(0, 0, 0); g.end(100);
    g.start(0, 0, 550);
    const [tap] = g.end(600);
    expect(tap.payload).toEqual({ durationMs: 50, intervalMs: 450 });
  });

  it('движение дальше порога даёт SWIPE с направлением и траекторией', () => {
    const g = new GestureTracker();
    g.start(300, 200, 0);
    g.move(250, 200, 40);
    g.move(200, 200, 80);
    g.move(120, 200, 150);
    const events = g.end(180);
    const swipe = events.find((e) => e.type === 'SWIPE')!;
    expect(swipe.payload).toMatchObject({ direction: 'LEFT', distancePx: 180, durationMs: 180 });
    const traj = events.find((e) => e.type === 'TOUCH_TRAJECTORY')!;
    expect(traj.payload).toMatchObject({ pointCount: 4, straightness: 1 });
  });

  it('вертикальный свайп вниз определяется по доминирующей оси', () => {
    const g = new GestureTracker();
    g.start(100, 100, 0);
    g.move(102, 180, 60);
    const [swipe] = g.end(100);
    expect(swipe.payload).toMatchObject({ direction: 'DOWN' });
  });

  it('cancel отбрасывает жест без событий', () => {
    const g = new GestureTracker();
    g.start(0, 0, 0);
    g.cancel();
    expect(g.end(10)).toEqual([]);
  });
});

describe('ScrollTracker', () => {
  it('объединяет серию смещений в одно прокручивание', () => {
    const s = new ScrollTracker();
    expect(s.onOffset(0, 0, 0)).toEqual([]);
    s.onOffset(0, 50, 100);
    s.onOffset(0, 120, 200);
    const [ev] = s.flush();
    expect(ev.type).toBe('SCROLL');
    expect(ev.payload).toMatchObject({ direction: 'DOWN', distancePx: 120, durationMs: 200 });
  });

  it('пауза больше порога закрывает предыдущее прокручивание', () => {
    const s = new ScrollTracker();
    s.onOffset(0, 0, 0);
    s.onOffset(0, 40, 100);
    const closed = s.onOffset(0, 300, 1000);
    expect(closed).toHaveLength(1);
    expect(closed[0].payload).toMatchObject({ direction: 'DOWN', distancePx: 40 });
  });
});

describe('TextInputTracker — без содержимого', () => {
  it('считает нажатия, Backspace и интервалы по длинам', () => {
    const t = new TextInputTracker('transfer.amount');
    t.onTextChange(0, 1, 1000);
    t.onTextChange(1, 2, 1200);
    t.onTextChange(2, 3, 1400);
    t.onTextChange(3, 2, 1600); // Backspace
    const [input] = t.flush();
    expect(input.type).toBe('TEXT_INPUT');
    expect(input.payload).toMatchObject({
      fieldId: 'transfer.amount',
      keyCount: 3,
      keyIntervalAvgMs: 200,
      keyIntervalStdMs: 0,
      backspaceCount: 1,
    });
  });

  it('рост текста на 2 и больше символа — вставка', () => {
    const t = new TextInputTracker('transfer.comment');
    t.onTextChange(0, 12, 1000);
    const events = t.flush();
    expect(events).toEqual([
      expect.objectContaining({ type: 'PASTE', payload: { fieldId: 'transfer.comment', pasteCount: 1 } }),
    ]);
  });

  it('в payload нет ни одного строкового поля кроме fieldId', () => {
    const t = new TextInputTracker('f.x');
    t.onTextChange(0, 1, 0);
    t.onTextChange(1, 2, 100);
    const [input] = t.flush();
    const stringFields = Object.entries(input.payload).filter(([, v]) => typeof v === 'string');
    expect(stringFields).toEqual([['fieldId', 'f.x']]);
  });
});

describe('IdleTracker, ScreenTracker, FormTracker', () => {
  it('пауза дольше порога фиксируется как IDLE', () => {
    const i = new IdleTracker(30_000);
    expect(i.onActivity(0)).toEqual([]);
    expect(i.onActivity(5_000)).toEqual([]);
    const [idle] = i.onActivity(45_000);
    expect(idle).toMatchObject({ type: 'IDLE', payload: { durationMs: 40_000 } });
  });

  it('переход между экранами даёт SCREEN_VIEW для предыдущего', () => {
    const s = new ScreenTracker();
    expect(s.track('home', 0)).toEqual([]);
    const [view] = s.track('transfer', 4_000);
    expect(view).toMatchObject({ type: 'SCREEN_VIEW', payload: { screen: 'home', dwellMs: 4_000 } });
  });

  it('исключённые экраны не попадают в события', () => {
    const s = new ScreenTracker(new Set(['pin']));
    s.track('pin', 0);
    expect(s.track('home', 9_000)).toEqual([]);
  });

  it('форма: время от старта до отправки', () => {
    const f = new FormTracker();
    f.start('transfer', 1_000);
    const [ev] = f.complete('transfer', 3, 8_500);
    expect(ev.payload).toEqual({ formId: 'transfer', durationMs: 7_500, fieldCount: 3 });
  });
});
