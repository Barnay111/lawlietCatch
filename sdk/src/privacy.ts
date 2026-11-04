/**
 * Защита от утечки содержимого на стороне SDK. Сервер проверяет то же самое ещё раз.
 */

/** Псевдоним поля: только строчные латинские буквы, цифры и . _ - */
export const FIELD_ID_PATTERN = /^[a-z0-9._-]{1,64}$/;

export function assertFieldId(fieldId: string): void {
  if (!FIELD_ID_PATTERN.test(fieldId)) {
    throw new Error(
      'BehaviorGuard: fieldId должен быть техническим именем вида "transfer.amount" (a-z, 0-9, . _ -)',
    );
  }
}

export function assertScreenName(name: string): void {
  if (!FIELD_ID_PATTERN.test(name)) {
    throw new Error('BehaviorGuard: имя экрана должно быть техническим, например "transfer"');
  }
}

/**
 * Разрешаем HTTPS. http допускаем только для localhost и только при явном флаге,
 * чтобы случайно не отправить события по открытому каналу.
 */
export function assertSecureUrl(apiUrl: string, allowInsecureLocalhost: boolean): void {
  let url: URL;
  try {
    url = new URL(apiUrl);
  } catch {
    throw new Error('BehaviorGuard: apiUrl не является корректным URL');
  }
  if (url.protocol === 'https:') {
    return;
  }
  const isLocal = url.hostname === 'localhost' || url.hostname === '127.0.0.1' || url.hostname === '10.0.2.2';
  if (url.protocol === 'http:' && isLocal && allowInsecureLocalhost) {
    return;
  }
  throw new Error('BehaviorGuard: разрешён только HTTPS для apiUrl');
}
