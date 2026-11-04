import React, { useState } from 'react';
import { Text } from 'react-native';
import { BehaviorGuard } from '@behaviorguard/mobile-sdk';
import { useBehaviorField } from '@behaviorguard/mobile-sdk/react';
import { Button, Card, Muted, Screen, colors } from '@/components/ui';
import { AccountPicker } from '@/components/AccountPicker';
import { Field } from '@/components/Field';
import { Result } from '@/components/Result';
import { fmt, nameForPhone, normalizePhone, recipientsFor, stateOf, transfer } from '@/lib/bank';
import { RecipientPicker } from '@/components/RecipientPicker';
import { useRequireLogin } from '@/lib/session';
import { useTrack } from '@/lib/track';

export default function PhoneTransferScreen() {
  const session = useRequireLogin();
  useTrack('transfer.phone');
  const userId = session.userId ?? '';
  const accounts = stateOf(userId).accounts;
  const [fromId, setFromId] = useState(accounts[0]?.id ?? '');
  const [phoneRaw, setPhoneRaw] = useState('');
  const [amount, setAmount] = useState('');
  const [comment, setComment] = useState('');
  const [result, setResult] = useState<{ ok: boolean; text: string } | null>(null);

  // Телефон — чувствительное поле.
  const phoneField = useBehaviorField('transfer.phone', { sensitive: true });
  const amountField = useBehaviorField('transfer.amount');
  const commentField = useBehaviorField('transfer.comment');

  const phone = normalizePhone(phoneRaw);
  const name = phone ? nameForPhone(phone) : null;

  const submit = () => {
    if (!phone) return;
    BehaviorGuard.formComplete('transfer.phone', 2);
    const r = transfer(userId, fromId, { kind: 'phone', phone }, Number(amount.replace(/\s/g, '')), comment.trim());
    if (r.ok) {
      setResult({ ok: true, text: `Переведено: ${r.recipient}. Баланс: ${fmt(r.newBalance)} ₸` });
      setAmount('');
      setComment('');
    } else {
      setResult({ ok: false, text: r.reason });
    }
  };

  return (
    <Screen>
      <Card>
        <Muted>Счёт списания</Muted>
        <AccountPicker accounts={accounts} value={fromId} onChange={setFromId} />
        <RecipientPicker recipients={recipientsFor(userId)} onPick={(r) => setPhoneRaw(r.phone)} />
        <Field
          label="Номер телефона получателя"
          value={phoneRaw}
          onChange={setPhoneRaw}
          bg={phoneField}
          placeholder="+7 701 000 00 00"
          keyboard="phone-pad"
          hint={phoneRaw ? (phone ? (name ? `Получатель: ${name}` : 'Получатель не найден в справочнике, перевод всё равно возможен') : 'Неверный формат номера') : 'Данные не анализируются'}
        />
      </Card>
      <Card>
        <Field label="Сумма, ₸" value={amount} onChange={setAmount} bg={amountField} keyboard="number-pad" />
        <Field label="Комментарий" value={comment} onChange={setComment} bg={commentField} />
      </Card>
      <Button label={name ? `Перевести: ${name}` : 'Перевести'} onPress={submit} disabled={!phone || !amount} />
      {result ? <Result ok={result.ok} text={result.text} /> : null}
      <Text style={{ color: colors.muted, textAlign: 'center', marginTop: 12, fontSize: 12 }}>
        Телефон хранится только в вашем приложении и не передаётся в аналитику.
      </Text>
    </Screen>
  );
}
