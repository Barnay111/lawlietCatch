import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { BehaviorGuardView } from '@behaviorguard/mobile-sdk/react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { SessionProvider } from '@/lib/session';
import { colors } from '@/components/ui';

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <SessionProvider>
        {/* Все касания приложения проходят через SDK; сами они не перехватываются. */}
        <BehaviorGuardView style={{ flex: 1 }}>
          <Stack
            screenOptions={{
              headerTintColor: '#FFFFFF',
              headerBackTitle: 'Назад',
              headerStyle: { backgroundColor: colors.brand },
              headerTitleStyle: { fontWeight: '700' },
              contentStyle: { backgroundColor: colors.bg },
            }}
          >
            <Stack.Screen name="index" options={{ headerShown: false }} />
            <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
            <Stack.Screen name="transfer/card" options={{ title: 'Перевод по карте' }} />
            <Stack.Screen name="transfer/phone" options={{ title: 'Перевод по телефону' }} />
            <Stack.Screen name="transfer/own" options={{ title: 'Мой банк' }} />
            <Stack.Screen name="transfer/qr" options={{ title: 'Оплата по QR' }} />
            <Stack.Screen name="transfer/receive" options={{ title: 'Получить деньги' }} />
            <Stack.Screen name="payments/[category]" options={{ title: 'Платежи' }} />
            <Stack.Screen name="payments/service/[id]" options={{ title: 'Оплата' }} />
          </Stack>
        </BehaviorGuardView>
        <StatusBar style="light" />
      </SessionProvider>
    </SafeAreaProvider>
  );
}
