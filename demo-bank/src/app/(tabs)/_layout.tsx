import React from 'react';
import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { colors } from '@/components/ui';
import { useRequireLogin } from '@/lib/session';

export default function TabsLayout() {
  useRequireLogin();
  return (
    <Tabs
      screenOptions={{
        headerStyle: { backgroundColor: colors.brand },
        headerTintColor: '#FFFFFF',
        headerTitleStyle: { fontWeight: '700' },
        tabBarActiveTintColor: colors.brand,
        tabBarInactiveTintColor: colors.muted,
      }}
    >
      <Tabs.Screen name="home" options={{ title: 'Главная', tabBarIcon: ({ color, size }) => <Ionicons name="home-outline" size={size} color={String(color)} /> }} />
      <Tabs.Screen name="transfers" options={{ title: 'Переводы', tabBarIcon: ({ color, size }) => <Ionicons name="swap-horizontal-outline" size={size} color={String(color)} /> }} />
      <Tabs.Screen name="payments" options={{ title: 'Платежи', tabBarIcon: ({ color, size }) => <Ionicons name="apps-outline" size={size} color={String(color)} /> }} />
      <Tabs.Screen name="messages" options={{ title: 'Сообщения', tabBarIcon: ({ color, size }) => <Ionicons name="chatbubbles-outline" size={size} color={String(color)} /> }} />
      <Tabs.Screen name="more" options={{ title: 'Ещё', tabBarIcon: ({ color, size }) => <Ionicons name="ellipsis-horizontal" size={size} color={String(color)} /> }} />
    </Tabs>
  );
}
