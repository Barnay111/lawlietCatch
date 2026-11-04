import React, { useState } from 'react';
import { Pressable, Text } from 'react-native';
import { BehaviorGuard } from '@behaviorguard/mobile-sdk';
import { useBehaviorField } from '@behaviorguard/mobile-sdk/react';
import { Button, Card, Muted, Screen, colors } from '@/components/ui';
import { AccountPicker } from '@/components/AccountPicker';
import { Field } from '@/components/Field';
import { Result } from '@/components/Result';
import { isValidPan, stateOf, transfer, formatPan, fmt, recipientsFor } from '@/lib/bank';
import { RecipientPicker } from '@/components/RecipientPicker';
import { useRequireLogin } from '@/lib/session';
import { useTrack } from '@/lib/track';

export default function CardTransferScreen() {
  const session = useRequireLogin();
  useTrack('transfer.card');
  const userId = session.userId ?? '';
  const accounts = stateOf(userId).accounts;
  const [fromId, setFromId] = useState(accounts[0]?.id ?? '');
  const [pan, setPan] = useState('');
  const [amount, setAmount] = useState('');
  const [comment, setComment] = useState('');
  const [result, setResult] = useState<{ ok: boolean; text: string } | null>(null);

  // Номер карты — чувствительное поле: SDK по нему ничего не собирает.
  const panField = useBehaviorField('transfer.card', { sensitive: true });
  const amountField = useBehaviorField('transfer.amount');
  const commentField = useBehaviorField('transfer.comment');

  const digits = pan.replace(/\D/g, '');
  const panOk = digits.length >= 13 && isValidPan(digits);

  const submit = () => {
    BehaviorGuard.formComplete('transfer.card', 3);
    const r = transfer(userId, fromId, { kind: 'card', pan: digits }, Number(amount.replace(/\s/g, '')), comment.trim());
    if (r.ok) {
      setResult({ ok: true, text: `Переведено на ${r.recipient}. Баланс счёта: ${fmt(r.newBalance)} ₸` });
      setAmount('');
      setComment('');
      setPan('');
    } else {
      setResult({ ok: false, text: r.reason });
    }
  };

  return (
    <Screen>
      <Card>
        <Muted>Счёт списания</Muted>
        <AccountPicker accounts={accounts} value={fromId} onChange={setFromId} />
        <RecipientPicker recipients={recipientsFor(userId)} onPick={(r) => setPan(r.pan)} />
        <Field
          label="Номер карты получателя"
          value={formatPan(pan)}
          onChange={setPan}
          bg={panField}
          placeholder="0000 0000 0000 0000"
          keyboard="number-pad"
          hint={digits.length >= 13 ? (panOk ? 'Номер карты корректен' : 'Проверьте номер карты') : 'Данные не анализируются'}
        />
      </Card>
      <Card>
        <Field label="Сумма, ₸" value={amount} onChange={setAmount} bg={amountField} keyboard="number-pad" />
        <Field label="Комментарий" value={comment} onChange={setComment} bg={commentField} />
      </Card>
      <Button label="Перевести" onPress={submit} disabled={!panOk || !amount} />
      {result ? <Result ok={result.ok} text={result.text} /> : null}
      <Pressable onPress={() => setResult(null)}>
        <Text style={{ textAlign: 'center', color: colors.brand, marginTop: 10 }}>Новый перевод</Text>
      </Pressable>
    </Screen>
  );
}
