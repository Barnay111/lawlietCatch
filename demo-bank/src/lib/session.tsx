import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { BehaviorGuard, SdkStats } from '@behaviorguard/mobile-sdk';
import { router } from 'expo-router';
import { BG_API_KEY, BG_API_URL } from './config';

interface SessionState {
  userId: string | null;
  consent: boolean;
  sessionActive: boolean;
  lastError: string | null;
  login(userId: string, consent: boolean): Promise<void>;
  logout(): Promise<void>;
  setConsent(granted: boolean): Promise<void>;
  stats(): SdkStats;
}

const Ctx = createContext<SessionState | null>(null);

let sdkReady = false;
function ensureSdk(): void {
  if (sdkReady) return;
  BehaviorGuard.init({
    apiKey: BG_API_KEY,
    apiUrl: BG_API_URL,
    consent: false,
    // Экран PIN и подтверждения операций не анализируем (в демо такого экрана нет).
    excludeScreens: ['pin'],
    // localhost по HTTP — только в режиме разработки.
    allowInsecureLocalhost: __DEV__,
    flushIntervalMs: 5_000,
  });
  sdkReady = true;
}

/**
 * Жизненный цикл сессии SDK в приложении:
 * - логин со согласием запускает сессию;
 * - уход в фон завершает сессию, возврат открывает новую;
 * - отзыв согласия останавливает сбор немедленно.
 */
export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [userId, setUserId] = useState<string | null>(null);
  const [consent, setConsentState] = useState(false);
  const [sessionActive, setSessionActive] = useState(false);
  const [lastError, setLastError] = useState<string | null>(null);
  const userRef = useRef<string | null>(null);
  const consentRef = useRef(false);

  const startIfNeeded = useCallback(async () => {
    const id = userRef.current;
    if (!id || !consentRef.current) return;
    try {
      await BehaviorGuard.startSession({ userId: id, deviceType: 'MOBILE' });
      setSessionActive(true);
      setLastError(null);
    } catch (e) {
      setSessionActive(false);
      setLastError(e instanceof Error ? e.message : 'Ошибка SDK');
    }
  }, []);

  useEffect(() => {
    ensureSdk();
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'background') {
        void BehaviorGuard.endSession().then(() => setSessionActive(false));
      } else if (state === 'active') {
        void startIfNeeded();
      }
    });
    return () => sub.remove();
  }, [startIfNeeded]);

  const login = useCallback(async (id: string, consentGranted: boolean) => {
    ensureSdk();
    userRef.current = id;
    consentRef.current = consentGranted;
    setUserId(id);
    setConsentState(consentGranted);
    BehaviorGuard.setConsent(consentGranted);
    if (consentGranted) {
      await startIfNeeded();
    }
  }, [startIfNeeded]);

  const logout = useCallback(async () => {
    await BehaviorGuard.endSession();
    userRef.current = null;
    setUserId(null);
    setSessionActive(false);
  }, []);

  const setConsent = useCallback(async (granted: boolean) => {
    consentRef.current = granted;
    setConsentState(granted);
    BehaviorGuard.setConsent(granted);
    if (granted) {
      await startIfNeeded();
    } else {
      setSessionActive(false);
    }
  }, [startIfNeeded]);

  const value: SessionState = {
    userId,
    consent,
    sessionActive,
    lastError,
    login,
    logout,
    setConsent,
    stats: () => BehaviorGuard.stats(),
  };
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useSession(): SessionState {
  const v = useContext(Ctx);
  if (!v) throw new Error('useSession вне SessionProvider');
  return v;
}

/** Экран доступен только после входа: иначе возвращаем на логин. */
export function useRequireLogin(): SessionState {
  const s = useSession();
  useEffect(() => {
    if (!s.userId) router.replace('/');
  }, [s.userId, router]);
  return s;
}
