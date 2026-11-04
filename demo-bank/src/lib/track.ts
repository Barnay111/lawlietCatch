import { useCallback } from 'react';
import { useFocusEffect } from 'expo-router';
import { BehaviorGuard } from '@behaviorguard/mobile-sdk';

/** Отмечает экран для SDK, когда он становится активным. Имена — технические, без данных. */
export function useTrack(name: string) {
  useFocusEffect(
    useCallback(() => {
      BehaviorGuard.trackScreen(name);
    }, [name]),
  );
}
