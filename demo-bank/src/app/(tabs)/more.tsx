import React from 'react';
import { StyleSheet, Switch, Text, View } from 'react-native';
import { router } from 'expo-router';
import type { SdkStats } from '@behaviorguard/mobile-sdk';
import { useEffect, useState } from 'react';
import { Button, Card, Muted, Screen, Title, colors } from '@/components/ui';
import { useRequireLogin } from '@/lib/session';
import { useTrack } from '@/lib/track';

export default function MoreScreen() {
  const session = useRequireLogin();
  useTrack('more');
  const [stats, setStats] = useState<SdkStats>(session.stats());
  useEffect(() => {
    const id = setInterval(() => setStats(session.stats()), 2_000);
    return () => clearInterval(id);
  }, [session]);

  return (
    <Screen>
      <Card>
        <Title>Клиент</Title>
        <Muted>Псевдоним: {session.userId ?? '—'}</Muted>
      </Card>

      <Card>
        <View style={styles.row}>
          <View style={{ flex: 1 }}>
            <Text style={styles.label}>Анализ поведения</Text>
            <Muted>{session.consent ? 'Согласие дано' : 'Согласие отозвано: сбор остановлен'}</Muted>
          </View>
          <Switch value={session.consent} onValueChange={(v) => void session.setConsent(v)} accessibilityLabel="Согласие на анализ поведения" />
        </View>
      </Card>

      <Card>
        <Text style={styles.label}>Состояние SDK</Text>
        <Muted>Сессия: {session.sessionActive ? 'активна' : 'не активна'}</Muted>
        <Muted>Отправлено событий: {stats.sent} · в очереди: {stats.pending}</Muted>
        {session.lastError ? <Text style={{ color: colors.danger }}>{session.lastError}</Text> : null}
      </Card>

      <Button
        label="Выйти"
        variant="danger"
        onPress={async () => {
          await session.logout();
          router.replace('/');
        }}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  label: { fontSize: 16, fontWeight: '600', color: colors.text, marginBottom: 4 },
});
