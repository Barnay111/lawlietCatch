import React, { useReducer } from 'react';
import { ScrollView, StyleSheet, Text, View, Pressable } from 'react-native';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { behaviorScrollProps } from '@behaviorguard/mobile-sdk/react';
import { Card, Muted, Screen, colors } from '@/components/ui';
import { stateOf, fmt, maskPan } from '@/lib/bank';
import { useRequireLogin } from '@/lib/session';
import { useTrack } from '@/lib/track';

const QUICK = [
  { label: 'Перевод', icon: 'swap-horizontal-outline', to: '/transfers' },
  { label: 'QR', icon: 'qr-code-outline', to: '/transfer/qr' },
  { label: 'Платежи', icon: 'apps-outline', to: '/payments' },
  { label: 'Мой банк', icon: 'wallet-outline', to: '/transfer/own' },
] as const;

export default function HomeScreen() {
  const session = useRequireLogin();
  useTrack('home');
  const [, refresh] = useReducer((x: number) => x + 1, 0);
  const s = stateOf(session.userId ?? '');
  const total = s.accounts.reduce((sum, a) => sum + a.balance, 0);

  return (
    <Screen>
      <ScrollView {...behaviorScrollProps()} contentContainerStyle={{ paddingBottom: 24 }}>
        <Card style={styles.hero}>
          <Text style={styles.heroLabel}>Общий баланс</Text>
          <Text style={styles.total}>{fmt(total)} ₸</Text>
          <Text style={styles.sub}>{s.cards[0] ? `Карта ${maskPan(s.cards[0].pan)} · ${s.cards[0].holder}` : ''}</Text>
        </Card>

        <Text style={styles.section}>Счета</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: 12 }}>
          {s.accounts.map((a) => (
            <Card key={a.id} style={styles.account}>
              <Text style={styles.accTitle}>{a.title}</Text>
              <Text style={styles.accMask}>{a.mask}</Text>
              <Text style={styles.accBalance}>{fmt(a.balance)} ₸</Text>
            </Card>
          ))}
        </ScrollView>

        <View style={styles.quick}>
          {QUICK.map((q) => (
            <Pressable key={q.label} style={styles.quickItem} onPress={() => router.push(q.to as never)} accessibilityRole="button">
              <View style={styles.quickIcon}>
                <Ionicons name={q.icon} size={24} color={colors.brand} />
              </View>
              <Text style={styles.quickLabel}>{q.label}</Text>
            </Pressable>
          ))}
        </View>

        <Text style={styles.section}>Последние операции</Text>
        {s.txs.slice(0, 6).map((tx) => (
          <Card key={tx.id} style={styles.tx}>
            <View style={{ flex: 1 }}>
              <Text style={styles.txTitle} numberOfLines={1}>{tx.title}</Text>
              <Muted>{tx.date} · {tx.category}</Muted>
            </View>
            <Text style={[styles.txAmount, { color: tx.amount < 0 ? colors.text : colors.ok }]}>
              {tx.amount > 0 ? '+' : ''}{fmt(tx.amount)} ₸
            </Text>
          </Card>
        ))}
        <Pressable onPress={refresh}>
          <Text style={styles.refresh}>Обновить</Text>
        </Pressable>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  hero: { backgroundColor: colors.brand, marginTop: 4 },
  heroLabel: { color: '#D6E0F0', fontSize: 14 },
  total: { fontSize: 32, fontWeight: '800', color: '#FFFFFF', marginTop: 4 },
  sub: { color: '#D6E0F0', marginTop: 4 },
  section: { fontSize: 16, fontWeight: '700', color: colors.text, marginTop: 10, marginBottom: 8 },
  account: { width: 200, marginRight: 10 },
  accTitle: { fontWeight: '700', color: colors.text },
  accMask: { color: colors.muted, fontSize: 12, marginTop: 2 },
  accBalance: { fontSize: 18, fontWeight: '800', color: colors.brand, marginTop: 8 },
  quick: { flexDirection: 'row', justifyContent: 'space-between', marginVertical: 8 },
  quickItem: { alignItems: 'center', width: 80 },
  quickIcon: { width: 56, height: 56, borderRadius: 18, backgroundColor: colors.brandSoft, alignItems: 'center', justifyContent: 'center' },
  quickLabel: { marginTop: 6, fontSize: 13, color: colors.text },
  tx: { flexDirection: 'row', alignItems: 'center', paddingVertical: 12 },
  txTitle: { fontSize: 15, fontWeight: '600', color: colors.text },
  txAmount: { fontSize: 15, fontWeight: '700' },
  refresh: { textAlign: 'center', color: colors.brand, marginTop: 8, fontWeight: '600' },
});
