import React, { useState } from 'react';
import { BehaviorGuard } from '@behaviorguard/mobile-sdk';
import { useBehaviorField } from '@behaviorguard/mobile-sdk/react';
import { useLocalSearchParams } from 'expo-router';
import { Button, Card, Muted, Screen, Title } from '@/components/ui';
import { AccountPicker } from '@/components/AccountPicker';
import { Field } from '@/components/Field';
import { Result } from '@/components/Result';
import { findService, fmt, pay, stateOf, ServiceField } from '@/lib/bank';
import { useRequireLogin } from '@/lib/session';
import { useTrack } from '@/lib/track';

/** Одна форма на любую услугу: поля берутся из каталога. Чувствительные поля не анализируются. */
function FieldInput({ f, value, onChange }: { f: ServiceField; value: string; onChange: (v: string) => void }) {
  const bg = useBehaviorField(`pay.${f.key}`, { sensitive: f.sensitive });
  const error = value && f.check ? f.check(value) : null;
  return (
    <Field
      label={f.label}
      value={value}
      onChange={onChange}
      bg={bg}
      placeholder={f.placeholder}
      keyboard={f.keyboard ?? 'default'}
      hint={error ?? (f.sensitive ? 'Данные не анализируются' : undefined)}
    />
  );
}

export default function ServicePayScreen() {
  const session = useRequireLogin();
  const { id } = useLocalSearchParams<{ id: string }>();
  const found = findService(id ?? '');
  const userId = session.userId ?? '';
  const accounts = stateOf(userId).accounts;
  const [fromId, setFromId] = useState(accounts[0]?.id ?? '');
  const [values, setValues] = useState<Record<string, string>>({});
  const [amount, setAmount] = useState(found?.service.fixedAmount ? String(found.service.fixedAmount) : '');
  const [result, setResult] = useState<{ ok: boolean; text: string } | null>(null);
  useTrack(`payments.service.${found?.category.id ?? 'unknown'}`);

  if (!found) {
    return (
      <Screen>
        <Muted>Услуга не найдена</Muted>
      </Screen>
    );
  }
  const { service } = found;
  const fieldsOk = service.fields.every((f) => (values[f.key] ?? '').trim().length > 0 && !(f.check?.(values[f.key] ?? '')));
  const fixed = service.fixedAmount !== undefined;

  const submit = () => {
    BehaviorGuard.formComplete(`pay.${service.id}`, service.fields.length + 1);
    const r = pay(userId, fromId, service, values, Number(amount.replace(/\s/g, '')));
    setResult(r.ok ? { ok: true, text: `Оплачено: ${service.title}. Баланс: ${fmt(r.newBalance)} ₸` } : { ok: false, text: r.reason });
    if (r.ok) setValues({});
  };

  return (
    <Screen>
      <Title>{service.title}</Title>
      <Muted>{service.provider}</Muted>
      <Card>
        <Muted>Счёт списания</Muted>
        <AccountPicker accounts={accounts} value={fromId} onChange={setFromId} />
        {service.fields.map((f) => (
          <FieldInput key={f.key} f={f} value={values[f.key] ?? ''} onChange={(v) => setValues((p) => ({ ...p, [f.key]: v }))} />
        ))}
        {fixed ? (
          <Muted>К оплате: {fmt(service.fixedAmount ?? 0)} ₸</Muted>
        ) : (
          <Field label="Сумма, ₸" value={amount} onChange={setAmount} bg={{ onChangeText: () => undefined, onBlur: () => undefined }} keyboard="number-pad" />
        )}
      </Card>
      <Button label="Оплатить" onPress={submit} disabled={!fieldsOk || !amount} />
      {result ? <Result ok={result.ok} text={result.text} /> : null}
    </Screen>
  );
}
