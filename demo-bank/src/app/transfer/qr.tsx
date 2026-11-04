import React, { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { BehaviorGuard } from '@behaviorguard/mobile-sdk';
import { useBehaviorField } from '@behaviorguard/mobile-sdk/react';
import { Button, Card, Muted, Screen, colors } from '@/components/ui';
import { AccountPicker } from '@/components/AccountPicker';
import { Result } from '@/components/Result';
import { fmt, parseQr, stateOf, transfer, QrPayload } from '@/lib/bank';
import { useRequireLogin } from '@/lib/session';
import { useTrack } from '@/lib/track';

/**
 * Оплата по QR. Камера распознаёт код; если камеры нет (симулятор), код можно вставить вручную.
 * Код содержит только реквизиты получателя и сумму, не персональные данные отправителя.
 */
export default function QrScreen() {
  const session = useRequireLogin();
  useTrack('transfer.qr');
  const userId = session.userId ?? '';
  const accounts = stateOf(userId).accounts;
  const [fromId, setFromId] = useState(accounts[0]?.id ?? '');
  const [permission, requestPermission] = useCameraPermissions();
  const [scanned, setScanned] = useState<QrPayload | null>(null);
  const [manual, setManual] = useState('');
  const [amount, setAmount] = useState('');
  const [result, setResult] = useState<{ ok: boolean; text: string } | null>(null);
  const manualField = useBehaviorField('transfer.qr.payload');

  const accept = (raw: string) => {
    const p = parseQr(raw);
    if (!p) {
      setResult({ ok: false, text: 'Это не код оплаты банка' });
      return;
    }
    setResult(null);
    setScanned(p);
    setAmount(p.amount ? String(p.amount) : '');
  };

  const pay = () => {
    if (!scanned) return;
    const r = transfer(userId, fromId, scanned.target, Number(amount.replace(/\s/g, '')), 'QR');
    setResult(r.ok ? { ok: true, text: `Оплачено: ${r.recipient}. Баланс: ${fmt(r.newBalance)} ₸` } : { ok: false, text: r.reason });
    if (r.ok) setScanned(null);
  };

  return (
    <Screen>
      {!scanned ? (
        <Card>
          <Muted>Наведите камеру на QR-код</Muted>
          {permission?.granted ? (
            <View style={styles.camera}>
              <CameraView
                style={StyleSheet.absoluteFill}
                barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
                onBarcodeScanned={({ data }) => accept(data)}
              />
            </View>
          ) : (
            <Button label="Разрешить камеру" onPress={() => void requestPermission()} />
          )}
          <Muted>Нет камеры или QR? Вставьте код вручную:</Muted>
          <TextInput
            style={styles.input}
            value={manual}
            onChangeText={(t) => {
              manualField.onChangeText(manual, t);
              setManual(t);
            }}
            onBlur={manualField.onBlur}
            placeholder="bankqr://pay?phone=+7..."
            placeholderTextColor="#9AA5B1"
            autoCapitalize="none"
            autoCorrect={false}
          />
          <Button label="Распознать код" variant="secondary" onPress={() => accept(manual)} disabled={!manual} />
        </Card>
      ) : (
        <Card>
          <Text style={styles.title}>Оплата</Text>
          <Muted>Получатель: {scanned.name ?? (scanned.target.kind === 'phone' ? scanned.target.phone : 'карта')}</Muted>
          <Muted>Со счёта</Muted>
          <AccountPicker accounts={accounts} value={fromId} onChange={setFromId} />
          <TextInput
            style={styles.input}
            value={amount}
            onChangeText={setAmount}
            keyboardType="number-pad"
            placeholder="Сумма, ₸"
            placeholderTextColor="#9AA5B1"
          />
          <Button label="Оплатить" onPress={pay} disabled={!amount} />
          <Pressable onPress={() => setScanned(null)}>
            <Text style={{ textAlign: 'center', color: colors.brand, marginTop: 10 }}>Отменить</Text>
          </Pressable>
        </Card>
      )}
      {result ? <Result ok={result.ok} text={result.text} /> : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  camera: { height: 260, borderRadius: 14, overflow: 'hidden', backgroundColor: '#000', marginVertical: 10 },
  input: { borderWidth: 1, borderColor: '#D5DCE6', borderRadius: 12, padding: 12, fontSize: 15, backgroundColor: '#FAFBFD', marginVertical: 8, color: colors.text },
  title: { fontSize: 18, fontWeight: '700', color: colors.text, marginBottom: 6 },
});
