import React, { useReducer } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Card, Muted, Screen, colors } from '@/components/ui';
import { markRead, stateOf } from '@/lib/bank';
import { useRequireLogin } from '@/lib/session';
import { useTrack } from '@/lib/track';

const ICON: Record<string, 'shield-checkmark-outline' | 'megaphone-outline' | 'business-outline' | 'headset-outline'> = {
  bank: 'business-outline',
  security: 'shield-checkmark-outline',
  promo: 'megaphone-outline',
  support: 'headset-outline',
};

export default function MessagesScreen() {
  const session = useRequireLogin();
  useTrack('messages');
  const [, refresh] = useReducer((x: number) => x + 1, 0);
  const msgs = stateOf(session.userId ?? '').messages;
  return (
    <Screen>
      <ScrollView contentContainerStyle={{ paddingBottom: 24 }}>
        {msgs.length === 0 ? <Muted>Сообщений пока нет.</Muted> : null}
        {msgs.map((m) => (
          <Pressable
            key={m.id}
            onPress={() => {
              markRead(session.userId ?? '', m.id);
              refresh();
            }}
            accessibilityRole="button"
          >
            <Card style={[styles.row, !m.read && styles.unread]}>
              <View style={styles.icon}>
                <Ionicons name={ICON[m.from]} size={20} color={colors.brand} />
              </View>
              <View style={{ flex: 1 }}>
                <View style={styles.head}>
                  <Text style={styles.title}>{m.title}</Text>
                  <Muted>{m.date}</Muted>
                </View>
                <Text style={styles.body}>{m.body}</Text>
              </View>
            </Card>
          </Pressable>
        ))}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: 12 },
  unread: { borderLeftWidth: 3, borderLeftColor: colors.accent },
  icon: { width: 36, height: 36, borderRadius: 12, backgroundColor: colors.brandSoft, alignItems: 'center', justifyContent: 'center' },
  head: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  title: { fontSize: 15, fontWeight: '700', color: colors.text, flex: 1, marginRight: 8 },
  body: { color: colors.text, fontSize: 14, lineHeight: 20, marginTop: 4 },
});
