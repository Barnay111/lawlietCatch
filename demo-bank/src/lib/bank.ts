/**
 * Демо-банк в памяти приложения. Не настоящий бэкенд: нужен, чтобы проверять экраны и поведение пользователя.
 * Реальные переводы, номера карт и справочники живут на стороне банка; здесь только учебные данные.
 */

export type AccountKind = 'current' | 'savings';

export interface Account {
  id: string;
  title: string;
  mask: string;
  kind: AccountKind;
  balance: number;
}

export interface Card {
  id: string;
  pan: string;
  holder: string;
  expiry: string;
  accountId: string;
}

export interface Tx {
  id: string;
  title: string;
  amount: number;
  date: string;
  category: string;
}

export interface Message {
  id: string;
  from: 'bank' | 'security' | 'promo' | 'support';
  title: string;
  body: string;
  date: string;
  read: boolean;
}

export type TransferTarget =
  | { kind: 'card'; pan: string }
  | { kind: 'phone'; phone: string }
  | { kind: 'own'; toAccountId: string };

export type TransferResult =
  | { ok: true; recipient: string; newBalance: number }
  | { ok: false; reason: string };

interface UserState {
  accounts: Account[];
  cards: Card[];
  txs: Tx[];
  messages: Message[];
}

const nowLabel = () => new Date().toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' });

const states = new Map<string, UserState>();

function seed(): UserState {
  return {
    accounts: [
      { id: 'current', title: 'Текущий счёт', mask: 'KZ**1234', kind: 'current', balance: 250_000 },
      { id: 'savings', title: 'Накопления', mask: 'KZ**9087', kind: 'savings', balance: 1_200_000 },
    ],
    cards: [{ id: 'c1', pan: '4400430000001234', holder: 'CLIENT DEMO', expiry: '11/29', accountId: 'current' }],
    txs: [
      { id: 't1', title: 'Магазин «Продукты»', amount: -12_400, date: '03.11', category: 'Покупки' },
      { id: 't2', title: 'Перевод от Анны К.', amount: 30_000, date: '02.11', category: 'Переводы' },
      { id: 't3', title: 'Кафе', amount: -5_800, date: '01.11', category: 'Питание' },
      { id: 't4', title: 'Оплата связи', amount: -3_500, date: '31.10', category: 'Связь' },
    ],
    messages: [
      { id: 'm1', from: 'bank', title: 'Добро пожаловать', body: 'Переводы, платежи и штрафы — в одном приложении.', date: '03.11', read: false },
      { id: 'm2', from: 'security', title: 'Вход в приложение', body: 'Вход выполнен с нового устройства. Если это не вы, свяжитесь с поддержкой.', date: '02.11', read: true },
      { id: 'm3', from: 'promo', title: 'Кешбэк 5% в путешествиях', body: 'Оплачивайте билеты и отели картой до конца месяца.', date: '01.11', read: true },
    ],
  };
}

export function stateOf(userId: string): UserState {
  let s = states.get(userId);
  if (!s) {
    s = seed();
    states.set(userId, s);
  }
  return s;
}

// ---------- проверки ----------

/** Алгоритм Луна для номера карты. */
export function isValidPan(pan: string): boolean {
  const digits = pan.replace(/\D/g, '');
  if (digits.length < 13 || digits.length > 19) return false;
  let sum = 0;
  let double = false;
  for (let i = digits.length - 1; i >= 0; i--) {
    let d = Number(digits[i]);
    if (double) {
      d *= 2;
      if (d > 9) d -= 9;
    }
    sum += d;
    double = !double;
  }
  return sum % 10 === 0;
}

/** Телефон в виде +7XXXXXXXXXX. Возвращает null, если формат не подходит. */
export function normalizePhone(input: string): string | null {
  const digits = input.replace(/\D/g, '');
  let d = digits;
  if (d.length === 11 && (d.startsWith('8') || d.startsWith('7'))) d = '7' + d.slice(1);
  if (d.length !== 10 && !(d.length === 11 && d.startsWith('7'))) return null;
  const full = d.length === 10 ? '7' + d : d;
  return '+' + full;
}

/** Контрольная цифра Луна: дописывает последний символ к номеру без него. */
export function completeLuhn(partial: string): string {
  for (let d = 0; d <= 9; d++) {
    const candidate = partial + d;
    if (isValidPan(candidate)) return candidate;
  }
  throw new Error('luhn');
}

/** Виртуальные клиенты банка: им можно переводить деньги. Баланс у них свой, в памяти приложения. */
export interface Recipient {
  id: string;
  name: string;
  phone: string;
  pan: string;
  city: string;
}

export const RECIPIENTS: Recipient[] = [
  { id: 'r-anna', name: 'Анна К.', phone: '+77012223344', pan: completeLuhn('440043000000710'), city: 'Алматы' },
  { id: 'r-marat', name: 'Марат С.', phone: '+77017654321', pan: completeLuhn('536299000001822'), city: 'Астана' },
  { id: 'r-aigerim', name: 'Айгерим Т.', phone: '+77079998877', pan: completeLuhn('440043000002934'), city: 'Шымкент' },
  { id: 'r-damir', name: 'Дамир Н.', phone: '+77055551010', pan: completeLuhn('411111000003046'), city: 'Караганда' },
  { id: 'r-liazzat', name: 'Ляззат Б.', phone: '+77781112233', pan: completeLuhn('520000000004158'), city: 'Алматы' },
  { id: 'r-serik', name: 'Серик Ж.', phone: '+77470001122', pan: completeLuhn('601100000005260'), city: 'Павлодар' },
];

/** Номер владельца приложения: на него можно получить деньги по своему QR. */
export const OWN_PHONE = '+77011234567';

export function findRecipientByPhone(phone: string): Recipient | null {
  return RECIPIENTS.find((r) => r.phone === phone) ?? null;
}

export function findRecipientByPan(pan: string): Recipient | null {
  const d = pan.replace(/\D/g, '');
  return RECIPIENTS.find((r) => r.pan === d) ?? null;
}

export function nameForPhone(phone: string): string | null {
  return findRecipientByPhone(phone)?.name ?? null;
}

export function recipientsFor(_userId: string): Recipient[] {
  return RECIPIENTS;
}

export function formatPan(pan: string): string {
  return pan.replace(/\D/g, '').replace(/(\d{4})(?=\d)/g, '$1 ');
}

export function maskPan(pan: string): string {
  const d = pan.replace(/\D/g, '');
  return `•••• ${d.slice(-4)}`;
}

export function fmt(n: number): string {
  return n.toLocaleString('ru-RU');
}

// ---------- операции ----------

function pushTx(s: UserState, title: string, amount: number, category: string) {
  s.txs.unshift({ id: 't' + Date.now(), title, amount, date: nowLabel(), category });
}

function pushMessage(s: UserState, title: string, body: string) {
  s.messages.unshift({ id: 'm' + Date.now(), from: 'bank', title, body, date: nowLabel(), read: false });
}

export function transfer(
  userId: string,
  fromAccountId: string,
  target: TransferTarget,
  amount: number,
  comment: string,
): TransferResult {
  const s = stateOf(userId);
  const from = s.accounts.find((a) => a.id === fromAccountId);
  if (!from) return { ok: false, reason: 'Счёт списания не найден' };
  if (!Number.isFinite(amount) || amount <= 0) return { ok: false, reason: 'Введите сумму больше нуля' };
  if (amount > from.balance) return { ok: false, reason: 'Недостаточно средств на счёте' };

  if (target.kind === 'own') {
    const to = s.accounts.find((a) => a.id === target.toAccountId);
    if (!to) return { ok: false, reason: 'Счёт зачисления не найден' };
    if (to.id === from.id) return { ok: false, reason: 'Выберите другой счёт' };
    from.balance -= amount;
    to.balance += amount;
    pushTx(s, `Между своими счетами: ${to.title}`, -amount, 'Переводы');
    return { ok: true, recipient: to.title, newBalance: from.balance };
  }

  let recipient: string;
  let credited: Recipient | null = null;
  if (target.kind === 'card') {
    if (!isValidPan(target.pan)) return { ok: false, reason: 'Номер карты указан неверно' };
    credited = findRecipientByPan(target.pan);
    recipient = credited ? `${credited.name} · ${maskPan(target.pan)}` : 'Карта ' + maskPan(target.pan);
  } else {
    if (target.phone === OWN_PHONE) return { ok: false, reason: 'Нельзя перевести самому себе. Используйте «Мой банк»' };
    credited = findRecipientByPhone(target.phone);
    recipient = credited ? credited.name : target.phone;
  }
  if (credited) {
    creditRecipient(credited, amount, `${SENDER_LABEL}${comment ? ' · ' + comment : ''}`);
  }
  from.balance -= amount;
  pushTx(s, `Перевод: ${recipient}${comment ? ' · ' + comment : ''}`, -amount, 'Переводы');
  if (amount >= 100_000) {
    pushMessage(s, 'Крупный перевод', `Списано ${fmt(amount)} ₸ на ${recipient}. Если это не вы — свяжитесь с банком.`);
  }
  return { ok: true, recipient, newBalance: from.balance };
}

/** Виртуальный получатель получает деньги на текущий счёт. */
const recipientStates = new Map<string, UserState>();
function creditRecipient(r: Recipient, amount: number, from: string) {
  let st = recipientStates.get(r.id);
  if (!st) {
    st = seedRecipient();
    recipientStates.set(r.id, st);
  }
  st.accounts[0].balance += amount;
  st.txs.unshift({ id: 't' + Date.now(), title: `Перевод от ${from}`, amount, date: nowLabel(), category: 'Переводы' });
  st.messages.unshift({ id: 'm' + Date.now(), from: 'bank', title: 'Зачисление', body: `Поступило ${fmt(amount)} ₸ от ${from}.`, date: nowLabel(), read: false });
}

/** Баланс виртуального клиента (для проверки зачислений). */
export function recipientBalance(id: string): number {
  const st = recipientStates.get(id);
  return st ? st.accounts[0].balance : seedRecipient().accounts[0].balance;
}

function seedRecipient(): UserState {
  return {
    accounts: [{ id: 'current', title: 'Текущий счёт', mask: 'KZ**0000', kind: 'current', balance: 100_000 }],
    cards: [],
    txs: [],
    messages: [],
  };
}

/** Подпись отправителя для получателя: без персональных данных отправителя. */
const SENDER_LABEL = 'клиента DemoBank';

/** Разбор QR-кода оплаты: bankqr://pay?to=+7...&amount=1000&name=... или bankqr://pay?card=... */
export interface QrPayload {
  target: TransferTarget;
  amount?: number;
  name?: string;
}

export function parseQr(payload: string): QrPayload | null {
  const m = payload.trim().match(/^bankqr:\/\/pay\?(.+)$/i);
  if (!m) return null;
  const params = new URLSearchParams(m[1]);
  const amount = params.get('amount');
  const name = params.get('name') ?? undefined;
  const phone = params.get('phone');
  const card = params.get('card');
  let target: TransferTarget | null = null;
  if (phone) {
    const p = normalizePhone(phone);
    if (p) target = { kind: 'phone', phone: p };
  } else if (card && isValidPan(card)) {
    target = { kind: 'card', pan: card };
  }
  if (!target) return null;
  return { target, amount: amount ? Number(amount) : undefined, name };
}

/** Код для получения денег: телефон владельца в QR. */
export function receiveCode(phone: string): string {
  return `bankqr://pay?phone=${encodeURIComponent(phone)}`;
}

// ---------- платежи и услуги ----------

export interface ServiceField {
  key: string;
  label: string;
  sensitive: boolean;
  placeholder?: string;
  keyboard?: 'default' | 'number-pad';
  /** Проверка значения; возвращает текст ошибки или null. */
  check?: (v: string) => string | null;
}

export interface Service {
  id: string;
  title: string;
  provider: string;
  fields: ServiceField[];
  /** Фиксированная сумма к оплате, если известна заранее. */
  fixedAmount?: number;
}

export interface Category {
  id: string;
  title: string;
  icon: 'phone-portrait-outline' | 'home-outline' | 'shield-checkmark-outline' | 'airplane-outline' | 'cart-outline' | 'film-outline';
  services: Service[];
}

const isIin = (v: string) => (/^\d{12}$/.test(v.replace(/\s/g, '')) ? null : 'ИИН: 12 цифр');

export const CATALOG: Category[] = [
  {
    id: 'shop',
    title: 'Покупки',
    icon: 'cart-outline',
    services: [
      {
        id: 'shop-order',
        title: 'Оплата заказа',
        provider: 'Интернет-магазин',
        fields: [{ key: 'order', label: 'Номер заказа', sensitive: false }],
      },
      {
        id: 'shop-subscription',
        title: 'Подписка',
        provider: 'Стриминг-сервис',
        fields: [{ key: 'account', label: 'Email аккаунта', sensitive: false }],
        fixedAmount: 2_490,
      },
    ],
  },
  {
    id: 'entertainment',
    title: 'Развлечения',
    icon: 'film-outline',
    services: [
      {
        id: 'cinema-tickets',
        title: 'Билеты в кино',
        provider: 'Кинотеатр',
        fields: [{ key: 'booking', label: 'Номер брони', sensitive: false }],
      },
      {
        id: 'fitness-membership',
        title: 'Абонемент в фитнес',
        provider: 'Фитнес-клуб',
        fields: [{ key: 'card', label: 'Номер клубной карты', sensitive: false }],
        fixedAmount: 15_000,
      },
    ],
  },
  {
    id: 'mobile',
    title: 'Мобильная связь',
    icon: 'phone-portrait-outline',
    services: [
      {
        id: 'mobile-topup',
        title: 'Пополнение баланса',
        provider: 'Оператор связи А',
        fields: [{ key: 'phone', label: 'Номер телефона', sensitive: true, placeholder: '+7 701 000 00 00', keyboard: 'number-pad', check: (v) => (normalizePhone(v) ? null : 'Неверный номер') }],
      },
    ],
  },
  {
    id: 'utilities',
    title: 'ЖКХ и интернет',
    icon: 'home-outline',
    services: [
      {
        id: 'utilities-electricity',
        title: 'Электроэнергия',
        provider: 'Энергосбыт',
        fields: [{ key: 'account', label: 'Лицевой счёт', sensitive: false, keyboard: 'number-pad' }],
      },
      {
        id: 'utilities-internet',
        title: 'Интернет',
        provider: 'Провайдер Б',
        fields: [{ key: 'contract', label: 'Номер договора', sensitive: false }],
      },
    ],
  },
  {
    id: 'gov',
    title: 'Госуслуги',
    icon: 'shield-checkmark-outline',
    services: [
      {
        id: 'gov-fines',
        title: 'Штрафы ГИБДД',
        provider: 'Портал штрафов',
        fields: [
          { key: 'iin', label: 'ИИН', sensitive: true, keyboard: 'number-pad', check: isIin },
          { key: 'decision', label: 'Номер постановления', sensitive: false },
        ],
      },
      {
        id: 'gov-tax',
        title: 'Налоги и взносы',
        provider: 'Налоговый орган',
        fields: [{ key: 'iin', label: 'ИИН', sensitive: true, keyboard: 'number-pad', check: isIin }],
      },
      {
        id: 'gov-duty',
        title: 'Госпошлина',
        provider: 'Госпошлина онлайн',
        fields: [{ key: 'iin', label: 'ИИН', sensitive: true, keyboard: 'number-pad', check: isIin }],
        fixedAmount: 3_000,
      },
    ],
  },
  {
    id: 'travel',
    title: 'Путешествия',
    icon: 'airplane-outline',
    services: [
      {
        id: 'travel-flight',
        title: 'Авиабилеты',
        provider: 'Авиакомпания В',
        fields: [{ key: 'booking', label: 'Номер бронирования', sensitive: false }],
      },
      {
        id: 'travel-hotel',
        title: 'Отели',
        provider: 'Сервис бронирования',
        fields: [{ key: 'booking', label: 'Номер брони', sensitive: false }],
      },
      {
        id: 'travel-train',
        title: 'Железнодорожные билеты',
        provider: 'Ж/д перевозчик',
        fields: [{ key: 'booking', label: 'Номер заказа', sensitive: false }],
      },
    ],
  },
];

export function findService(id: string): { category: Category; service: Service } | null {
  for (const c of CATALOG) {
    const s = c.services.find((x) => x.id === id);
    if (s) return { category: c, service: s };
  }
  return null;
}

export function pay(
  userId: string,
  fromAccountId: string,
  service: Service,
  values: Record<string, string>,
  amount: number,
): TransferResult {
  const s = stateOf(userId);
  const from = s.accounts.find((a) => a.id === fromAccountId);
  if (!from) return { ok: false, reason: 'Счёт списания не найден' };
  if (!Number.isFinite(amount) || amount <= 0) return { ok: false, reason: 'Введите сумму больше нуля' };
  if (amount > from.balance) return { ok: false, reason: 'Недостаточно средств на счёте' };
  from.balance -= amount;
  pushTx(s, `${service.title}: ${service.provider}`, -amount, 'Платежи');
  pushMessage(s, 'Платёж проведён', `${service.title}: ${fmt(amount)} ₸. Квитанция сохранена в истории.`);
  return { ok: true, recipient: service.provider, newBalance: from.balance };
}

export function travelPromo(): Message {
  return { id: 'promo-travel', from: 'promo', title: 'Кешбэк 5% в путешествиях', body: 'Оплачивайте билеты и отели картой.', date: nowLabel(), read: true };
}

export function markRead(userId: string, id: string) {
  const m = stateOf(userId).messages.find((x) => x.id === id);
  if (m) m.read = true;
}
