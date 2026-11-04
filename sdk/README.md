# BehaviorGuard Mobile SDK (React Native)

Сбор поведенческих сигналов в мобильном приложении банка. Сигналы агрегируются на устройстве,
в API уходят только числа, перечисления и технические имена полей. Содержимое полей не передаётся.

## Подключение

```ts
import { BehaviorGuard } from '@behaviorguard/mobile-sdk';
// React-слой (отдельный подпуть, чтобы ядро не тянуло react-native):
import { BehaviorGuardView, useBehaviorField, behaviorScrollProps } from '@behaviorguard/mobile-sdk/react';

BehaviorGuard.init({
  apiKey: 'bg_pk_...',                 // публичный ключ интеграции (не секрет)
  apiUrl: 'https://api.example.com',   // только HTTPS
  consent: true,                       // явное согласие пользователя
  excludeScreens: ['pin'],             // экраны, которые не анализируются
});

await BehaviorGuard.startSession({ userId: 'client-0007' }); // только псевдоним
BehaviorGuard.trackPage('transfer');
await BehaviorGuard.endSession();
BehaviorGuard.setConsent(false);       // отзыв: сбор останавливается, буфер очищается
```

Корень приложения оборачивается в `BehaviorGuardView` (касания и свайпы), прокрутка подключается через
`<ScrollView {...behaviorScrollProps()}>`, текстовые поля — через `useBehaviorField`:

```tsx
const amount = useBehaviorField('transfer.amount');
const card = useBehaviorField('transfer.card', { sensitive: true }); // не анализируется вообще
<TextInput
  onChangeText={(next) => { amount.onChangeText(value, next); setValue(next); }}
  onBlur={amount.onBlur}
/>
```

## Какие сигналы из ТЗ собираются на мобильных

| № ТЗ | Сигнал | На мобильных | Событие |
|---|---|---|---|
| 1 | Mouse movement | заменён траекторией касания | `TOUCH_TRAJECTORY` |
| 2 | Mouse speed | скорость жеста | `SWIPE.avgSpeedPxS` |
| 3 | Mouse acceleration | **не собирается**: у касания нет аналога мышиного ускорения; ускорение жеста можно вычислить, но это отдельная задача | — |
| 4 | Click duration | длительность касания | `TAP.durationMs` |
| 5 | Click interval | интервал между касаниями | `TAP.intervalMs` |
| 6 | Typing speed | символов в минуту | `TEXT_INPUT.charsPerMinute` |
| 7 | Key hold time | **не собирается** в React Native без нативного модуля | — |
| 8 | Key interval | среднее и σ интервала между нажатиями | `TEXT_INPUT.keyInterval*` |
| 9 | Backspace frequency | число Backspace | `TEXT_INPUT.backspaceCount` |
| 10 | Scroll speed | скорость прокрутки | `SCROLL.avgSpeedPxS` |
| 11 | Scroll direction | направление | `SCROLL.direction` |
| 12 | Touch movement | траектория | `TOUCH_TRAJECTORY` |
| 13 | Swipe speed | скорость свайпа | `SWIPE.avgSpeedPxS` |
| 14 | Swipe duration | длительность свайпа | `SWIPE.durationMs` |
| 15 | Navigation sequence | переходы между экранами | `SCREEN_VIEW` (порядок) |
| 16 | Page dwell time | время на экране | `SCREEN_VIEW.dwellMs` |
| 17 | Form completion time | от старта формы до отправки | `FORM_COMPLETION.durationMs` |
| 18 | Paste events | факт вставки (по скачку длины) | `PASTE.pasteCount` |
| 19 | Idle time | паузы дольше 30 секунд | `IDLE.durationMs` |
| 20 | Session duration | длительность сессии | вычисляется на сервере по `sessions` |

Ограничения, о которых нужно знать заранее:
- **Вставка** определяется по росту текста на 2 и более символа за одно изменение. Предиктивный ввод
  клавиатуры может дать ложное срабатывание. Это эвристика, не факт.
- **Нажатия** считаются по изменению длины текста, а не по `onKeyPress`: на Android `onKeyPress`
  ненадёжен для программных клавиатур.
- **Key hold time** требует нативного модуля (`onKeyPress` не даёт время отпускания клавиши). **Mouse acceleration** на касаниях не имеет прямого аналога.

## Приватность

- Символы, значения полей и содержимое буфера обмена не читаются: SDK видит только длины строк.
- Поля с `sensitive: true` не подключаются вообще: ни тайминги, ни счётчики.
- Экраны из `excludeScreens` не попадают в события.
- На сервере тот же список разрешённых полей проверяется повторно (`EventType.java`):
  неизвестное поле отклоняет весь пакет.
- Очередь хранится только в памяти: при закрытии приложения несохранённое теряется.
  На диск поведенческие данные не пишутся.
- Отзыв согласия отбрасывает буфер и закрывает сессию.

## Транспорт

- Пакеты по 50 событий, отправка каждые 10 секунд или при заполнении.
- Лимит 600 событий в минуту на сессию; сверх лимита события отбрасываются и считаются.
- Повтор только при сетевой ошибке и 5xx. 4xx не повторяется.
- 401/403 отключают сбор до перезапуска: ключ не принят, отправка бессмысленна.

## Тесты

```bash
cd sdk
npm test        # Jest: агрегаторы, приватность, согласие, транспорт
npm run typecheck
```

Тесты приложения и SDK на устройстве — в `demo-bank/` (iOS-симулятор).
