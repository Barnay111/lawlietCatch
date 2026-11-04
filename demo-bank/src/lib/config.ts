export const BG_API_URL = process.env.EXPO_PUBLIC_BG_API_URL ?? 'http://localhost:8080';
export const BG_API_KEY = process.env.EXPO_PUBLIC_BG_API_KEY ?? '';
/** Только для разработки и снимков экрана: вход без экрана логина. Не включать в сборки для пользователей. */
export const DEMO_AUTOLOGIN = __DEV__ && process.env.EXPO_PUBLIC_DEMO_AUTOLOGIN === '1';
