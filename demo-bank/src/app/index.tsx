import React, { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { router } from 'expo-router';
import { useBehaviorField, BehaviorGuardView } from '@behaviorguard/mobile-sdk/react';
import { Button, Card, Muted, Screen, Title, colors } from '@/components/ui';
import { useSession } from '@/lib/session';
import { DEMO_AUTOLOGIN } from '@/lib/config';

const DEMO_CLIENT_ID = 'client-0007';

export default function LoginScreen() {
  const session = useSession();
  const [login, setLogin] = useState(DEMO_CLIENT_ID);
  const [consent, setConsent] = useState(false);
  const [busy, setBusy] = useState(false);
  // Логин — чувствительное поле: сбор с него не ведётся.
  const loginField = useBehaviorField('login', { sensitive: true });

  // Вход: после логина открываем табы.
  useEffect(() => {
    if (session.userId) router.replace('/(tabs)/home');
  }, [session.userId]);

  // Служебный вход для снимков экрана в разработке (EXPO_PUBLIC_DEMO_AUTOLOGIN=1).
  useEffect(() => {
    if (DEMO_AUTOLOGIN && !session.userId) void session.login(DEMO_CLIENT_ID, true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const submit = async () => {
    setBusy(true);
    await session.login(login.trim(), consent);
    setBusy(false);
  };

  return (
    <Screen>
      <View style={styles.hero}>
        <Text style={styles.logo}>DemoBank</Text>
        <Text style={styles.heroSub}>Переводы, платежи и услуги в одном приложении</Text>
      </View>

      <Card>
        <Title>Вход</Title>
        <Muted>Демо-вход без пароля. Укажите псевдоним клиента, не ФИО и не телефон.</Muted>
        <TextInput
          style={styles.input}
          value={login}
          onChangeText={(t) => {
            loginField.onChangeText(login, t);
            setLogin(t);
          }}
          onBlur={loginField.onBlur}
          autoCapitalize="none"
          autoCorrect={false}
          accessibilityLabel="Псевдоним клиента"
        />
      </Card>

      <Card>
        <Pressable style={styles.consentRow} onPress={() => setConsent((c) => !c)} accessibilityRole="checkbox">
          <View style={[styles.checkbox, consent && styles.checkboxOn]}>
            {consent ? <Text style={styles.tick}>✓</Text> : null}
          </View>
          <Text style={styles.consentText}>
            Я согласен на анализ поведения при использовании приложения (скорость касаний, набора и переходов).
            Содержимое полей не записывается. Согласие можно отозвать в разделе «Ещё».
          </Text>
        </Pressable>
      </Card>

      <Button label="Войти" onPress={submit} disabled={!consent || login.trim().length === 0 || busy} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  hero: { backgroundColor: colors.brand, marginHorizontal: -16, marginTop: -16, padding: 24, paddingTop: 48, marginBottom: 16 },
  logo: { color: colors.accent, fontSize: 30, fontWeight: '800' },
  heroSub: { color: '#D6E0F0', marginTop: 4 },
  input: { borderWidth: 1, borderColor: '#D5DCE6', borderRadius: 12, padding: 12, fontSize: 16, marginTop: 8, backgroundColor: '#FAFBFD' },
  consentRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 12 },
  checkbox: { width: 24, height: 24, borderRadius: 6, borderWidth: 2, borderColor: colors.brand, alignItems: 'center', justifyContent: 'center' },
  checkboxOn: { backgroundColor: colors.brand },
  tick: { color: '#FFFFFF', fontWeight: '700' },
  consentText: { flex: 1, color: colors.text, fontSize: 14, lineHeight: 20 },
});
