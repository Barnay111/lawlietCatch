import React from 'react';
import { Pressable, Text } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { Card, Muted, Screen, Title, colors } from '@/components/ui';
import { CATALOG } from '@/lib/bank';
import { useRequireLogin } from '@/lib/session';
import { useTrack } from '@/lib/track';

export default function CategoryScreen() {
  useRequireLogin();
  const { category } = useLocalSearchParams<{ category: string }>();
  const cat = CATALOG.find((c) => c.id === category);
  useTrack(`payments.${cat?.id ?? 'unknown'}`);
  if (!cat) {
    return (
      <Screen>
        <Muted>Категория не найдена</Muted>
      </Screen>
    );
  }
  return (
    <Screen>
      <Title>{cat.title}</Title>
      {cat.services.map((s) => (
        <Pressable key={s.id} onPress={() => router.push(`/payments/service/${s.id}` as never)} accessibilityRole="button">
          <Card>
            <Text style={{ fontSize: 16, fontWeight: '700', color: colors.text }}>{s.title}</Text>
            <Muted>{s.provider}{s.fixedAmount ? ` · ${s.fixedAmount.toLocaleString('ru-RU')} ₸` : ''}</Muted>
          </Card>
        </Pressable>
      ))}
    </Screen>
  );
}
