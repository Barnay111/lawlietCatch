import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { Card, Muted, Screen, colors } from '@/components/ui';
import { useRequireLogin } from '@/lib/session';
import { useTrack } from '@/lib/track';

const ITEMS = [
  { title: 'По номеру карты', sub: 'Перевод на карту любого банка', icon: 'card-outline', to: '/transfer/card' },
  { title: 'По номеру телефона', sub: 'Получатель по номеру мобильного', icon: 'call-outline', to: '/transfer/phone' },
  { title: 'Между своими счетами', sub: 'Мой банк: без комиссии', icon: 'wallet-outline', to: '/transfer/own' },
  { title: 'По QR-коду', sub: 'Отсканируйте код получателя', icon: 'scan-outline', to: '/transfer/qr' },
  { title: 'Получить деньги', sub: 'Покажите свой QR-код', icon: 'qr-code-outline', to: '/transfer/receive' },
] as const;

export default function TransfersScreen() {
  useRequireLogin();
  useTrack('transfers');
  return (
    <Screen>
      {ITEMS.map((i) => (
        <Pressable key={i.title} onPress={() => router.push(i.to as never)} accessibilityRole="button">
          <Card style={styles.row}>
            <View style={styles.icon}>
              <Ionicons name={i.icon} size={22} color={colors.brand} />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.title}>{i.title}</Text>
              <Muted>{i.sub}</Muted>
            </View>
            <Ionicons name="chevron-forward" size={18} color={colors.muted} />
          </Card>
        </Pressable>
      ))}
    </Screen>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  icon: { width: 44, height: 44, borderRadius: 14, backgroundColor: colors.brandSoft, alignItems: 'center', justifyContent: 'center' },
  title: { fontSize: 16, fontWeight: '700', color: colors.text },
});
