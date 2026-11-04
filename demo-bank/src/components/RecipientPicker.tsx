import React from 'react';
import { Pressable, ScrollView, StyleSheet, Text } from 'react-native';
import { Recipient } from '@/lib/bank';
import { colors } from '@/components/ui';

/** Быстрый выбор получателя из списка клиентов банка. */
export function RecipientPicker({ recipients, onPick }: { recipients: Recipient[]; onPick: (r: Recipient) => void }) {
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: 8 }}>
      {recipients.map((r) => (
        <Pressable key={r.id} onPress={() => onPick(r)} accessibilityRole="button" style={styles.chip}>
          <Text style={styles.name}>{r.name}</Text>
          <Text style={styles.sub}>{r.city}</Text>
        </Pressable>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  chip: { backgroundColor: colors.brandSoft, borderRadius: 14, paddingVertical: 8, paddingHorizontal: 12, marginRight: 8 },
  name: { fontWeight: '700', color: colors.brand },
  sub: { color: colors.muted, fontSize: 12, marginTop: 2 },
});
