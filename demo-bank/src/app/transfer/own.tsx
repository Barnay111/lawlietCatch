import React, { useState } from 'react';
import { BehaviorGuard } from '@behaviorguard/mobile-sdk';
import { useBehaviorField } from '@behaviorguard/mobile-sdk/react';
import { Button, Card, Muted, Screen, Title } from '@/components/ui';
import { AccountPicker } from '@/components/AccountPicker';
import { Field } from '@/components/Field';
import { Result } from '@/components/Result';
import { fmt, stateOf, transfer } from '@/lib/bank';
import { useRequireLogin } from '@/lib/session';
import { useTrack } from '@/lib/track';

export default function OwnTransferScreen() {
  const session = useRequireLogin();
  useTrack('transfer.own');
  const userId = session.userId ?? '';
  const accounts = stateOf(userId).accounts;
  const [fromId, setFromId] = useState(accounts[0]?.id ?? '');
  const [toId, setToId] = useState(accounts[1]?.id ?? '');
  const [amount, setAmount] = useState('');
  const [result, setResult] = useState<{ ok: boolean; text: string } | null>(null);
  const amountField = useBehaviorField('transfer.amount');

  const submit = () => {
    BehaviorGuard.formComplete('transfer.own', 1);
    const r = transfer(userId, fromId, { kind: 'own', toAccountId: toId }, Number(amount.replace(/\s/g, '')), '');
    setResult(r.ok ? { ok: true, text: `Переведено на «${r.recipient}». Баланс списания: ${fmt(r.newBalance)} ₸` } : { ok: false, text: r.reason });
    if (r.ok) setAmount('');
  };

  return (
    <Screen>
      <Title>Между своими счетами</Title>
      <Muted>Без комиссии, мгновенно.</Muted>
      <Card>
        <Muted>Со счёта</Muted>
        <AccountPicker accounts={accounts} value={fromId} onChange={setFromId} />
        <Muted>На счёт</Muted>
        <AccountPicker accounts={accounts.filter((a) => a.id !== fromId)} value={toId} onChange={setToId} />
        <Field label="Сумма, ₸" value={amount} onChange={setAmount} bg={amountField} keyboard="number-pad" />
      </Card>
      <Button label="Перевести" onPress={submit} disabled={!amount || fromId === toId} />
      {result ? <Result ok={result.ok} text={result.text} /> : null}
    </Screen>
  );
}
