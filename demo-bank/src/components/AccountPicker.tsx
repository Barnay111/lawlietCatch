import React from 'react';
import { Pressable, ScrollView, StyleSheet, Text } from 'react-native';
import { Account } from '@/lib/bank';
import { colors } from '@/components/ui';

export function AccountPicker({
  accounts,
  value,
  onChange,
}: {
  accounts: Account[];
  value: string;
  onChange: (id: string) => void;
}) {
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: 12 }}>
      {accounts.map((a) => {
        const active = a.id === value;
        return (
          <Pressable
            key={a.id}
            onPress={() => onChange(a.id)}
            accessibilityRole="button"
            style={[styles.chip, active && styles.chipActive]}
          >
            <Text style={[styles.title, active && { color: '#FFFFFF' }]}>{a.title}</Text>
            <Text style={[styles.sub, active && { color: '#D6E0F0' }]}>{a.mask}</Text>
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  chip: { backgroundColor: '#FFFFFF', borderRadius: 14, paddingVertical: 10, paddingHorizontal: 14, marginRight: 10, borderWidth: 1, borderColor: '#D5DCE6', minWidth: 150 },
  chipActive: { backgroundColor: colors.brand, borderColor: colors.brand },
  title: { fontWeight: '700', color: colors.text },
  sub: { color: colors.muted, marginTop: 2, fontSize: 12 },
});
