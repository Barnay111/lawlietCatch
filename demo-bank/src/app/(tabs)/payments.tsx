import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { Muted, Screen, colors } from '@/components/ui';
import { CATALOG } from '@/lib/bank';
import { useRequireLogin } from '@/lib/session';
import { useTrack } from '@/lib/track';

export default function PaymentsScreen() {
  useRequireLogin();
  useTrack('payments');
  return (
    <Screen>
      <Text style={styles.header}>Оплатить и перевести</Text>
      <View style={styles.grid}>
        {CATALOG.map((c) => (
          <Pressable key={c.id} style={styles.tile} onPress={() => router.push(`/payments/${c.id}` as never)} accessibilityRole="button">
            <View style={styles.icon}>
              <Ionicons name={c.icon} size={26} color={colors.brand} />
            </View>
            <Text style={styles.title}>{c.title}</Text>
            <Muted>{c.services.length} услуг</Muted>
          </Pressable>
        ))}
      </View>
      <Muted>Штрафы, налоги, пошлины и услуги операторов — в категориях выше.</Muted>
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { fontSize: 22, fontWeight: '800', color: colors.text, marginBottom: 12 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', marginBottom: 12 },
  tile: { width: '48%', backgroundColor: '#FFFFFF', borderRadius: 16, padding: 14, marginBottom: 12, borderWidth: 1, borderColor: '#E3E8EF' },
  icon: { width: 48, height: 48, borderRadius: 14, backgroundColor: colors.brandSoft, alignItems: 'center', justifyContent: 'center', marginBottom: 10 },
  title: { fontSize: 15, fontWeight: '700', color: colors.text, marginBottom: 2 },
});
